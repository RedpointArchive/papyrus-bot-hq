package games.redpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.protocol.bedrock.BedrockClient;
import com.nukkitx.protocol.bedrock.BedrockPong;
import com.nukkitx.protocol.bedrock.BedrockServer;
import com.nukkitx.protocol.bedrock.BedrockServerEventHandler;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import com.nukkitx.protocol.bedrock.packet.LoginPacket;
import com.nukkitx.protocol.bedrock.v354.Bedrock_v354;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import io.netty.util.AsciiString;
import net.minidev.json.JSONObject;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import java.net.URI;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Hello world!
 *
 */
public class App {
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    public static void main(String[] args) {
        App app = new App();
        app.boot();
    }
    
    public void boot() {
        InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", 0);
        BedrockClient client = new BedrockClient(bindAddress);

        client.bind().join();

        InetSocketAddress addressToConnect = new InetSocketAddress("34.94.110.9", 19132);

        System.out.println("Connected");
        client.connect(addressToConnect).whenComplete((session, throwable) -> {
            if (throwable != null) {
                System.out.println(throwable);
                return;
            }
            session.setPacketCodec(Bedrock_v354.V354_CODEC);

            ArrayNode chainData;

            KeyPair proxyKeyPair = EncryptionUtils.createKeyPair();
            ObjectMapper jsonMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            JsonNode certData;
            try {
                // todo: packet doesn't exist
                certData = jsonMapper.readTree(packet.getChainData().toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("Certificate JSON can not be read.");
            }
    
            JsonNode certChainData = certData.get("chain");
            if (certChainData.getNodeType() != JsonNodeType.ARRAY) {
                throw new RuntimeException("Certificate data is not valid");
            }
            chainData = (ArrayNode) certChainData;
    
            JSONObject skinData;
            JSONObject extraData;
            AuthData authData;
            JWSObject jwt = JWSObject.parse(certChainData.get(certChainData.size() - 1).asText());
            JsonNode payload = jsonMapper.readTree(jwt.getPayload().toBytes());

            if (payload.get("extraData").getNodeType() != JsonNodeType.OBJECT) {
                throw new RuntimeException("AuthData was not found!");
            }

            extraData = (JSONObject) jwt.getPayload().toJSONObject().get("extraData");

            authData = new AuthData(extraData.getAsString("displayName"),
                    UUID.fromString(extraData.getAsString("identity")), extraData.getAsString("XUID"));

            if (payload.get("identityPublicKey").getNodeType() != JsonNodeType.STRING) {
                throw new RuntimeException("Identity Public Key was not found!");
            }
            ECPublicKey identityPublicKey = EncryptionUtils.generateKey(payload.get("identityPublicKey").textValue());

            // todo: packet doesn't exist
            JWSObject clientJwt = JWSObject.parse(packet.getSkinData().toString());
            skinData = clientJwt.getPayload().toJSONObject();

            SignedJWT authDataSigned = forgeAuthData(proxyKeyPair, extraData);
            JWSObject skinDataSigned = forgeSkinData(proxyKeyPair, skinData);
            chainData.remove(chainData.size() - 1);
            chainData.add(authDataSigned.serialize());
            JsonNode json = jsonMapper.createObjectNode().set("chain", chainData);
            AsciiString chainDataText;
            try {
                chainDataText = new AsciiString(jsonMapper.writeValueAsBytes(json));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            LoginPacket login = new LoginPacket();
            login.setChainData(chainDataText);
            login.setSkinData(AsciiString.of(skinDataSigned.serialize()));
            login.setProtocolVersion(Bedrock_v354.V354_CODEC.getProtocolVersion());
            
            session.sendPacketImmediately(login);
            //session.setBatchedHandler(new BatchedHandler());
            session.setLogging(true);
            session.setPacketHandler(new PacketHandler());

            session.addDisconnectHandler(disconnectReason -> {
                running.set(false);
                synchronized (this) {
                    this.notify();
                }
                
                System.out.println(disconnectReason);
                System.out.println("Disconnected");
            });

            System.out.println("Connected");
        });

        while (running.get()) {
            try {
                synchronized(this) {
                    this.wait();
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }

        System.out.println("Main exiting");
    }
    
    public static SignedJWT forgeAuthData(KeyPair pair, JSONObject extraData) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        URI x5u = URI.create(publicKeyBase64);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES384).x509CertURL(x5u).build();

        long timestamp = System.currentTimeMillis();
        Date nbf = new Date(timestamp - TimeUnit.SECONDS.toMillis(1));
        Date exp = new Date(timestamp + TimeUnit.DAYS.toMillis(1));

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .notBeforeTime(nbf)
                .expirationTime(exp)
                .issueTime(exp)
                .issuer("self")
                .claim("certificateAuthority", true)
                .claim("extraData", extraData)
                .claim("identityPublicKey", publicKeyBase64)
                .build();

        SignedJWT jwt = new SignedJWT(header, claimsSet);

        try {
            EncryptionUtils.signJwt(jwt, (ECPrivateKey) pair.getPrivate());
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }

        return jwt;
    }

    public static JWSObject forgeSkinData(KeyPair pair, JSONObject skinData) {
        URI x5u = URI.create(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES384).x509CertURL(x5u).build();

        JWSObject jws = new JWSObject(header, new Payload(skinData));

        try {
            EncryptionUtils.signJwt(jws, (ECPrivateKey) pair.getPrivate());
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }

        return jws;
    }
}
