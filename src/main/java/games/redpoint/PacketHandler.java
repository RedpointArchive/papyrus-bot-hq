package games.redpoint;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Random;

import javax.crypto.SecretKey;

import com.flowpowered.math.vector.Vector2f;
import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.protocol.bedrock.BedrockClientSession;
import com.nukkitx.protocol.bedrock.data.GamePublishSetting;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.ClientToServerHandshakePacket;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.packet.PlayStatusPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackStackPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePacksInfoPacket;
import com.nukkitx.protocol.bedrock.packet.ServerToClientHandshakePacket;
import com.nukkitx.protocol.bedrock.packet.StartGamePacket;
import com.nukkitx.protocol.bedrock.packet.TextPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackClientResponsePacket.Status;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;

public class PacketHandler implements BedrockPacketHandler {
    private final BedrockClientSession session;
    private final KeyPair proxyKeyPair;

    public PacketHandler(BedrockClientSession session, KeyPair proxyKeyPair) {
        this.session = session;
        this.proxyKeyPair = proxyKeyPair;
    }

    @Override
    public boolean handle(ServerToClientHandshakePacket packet) {
        try {
            SignedJWT saltJwt = SignedJWT.parse(packet.getJwt());
            URI x5u = saltJwt.getHeader().getX509CertURL();
            ECPublicKey serverKey = EncryptionUtils.generateKey(x5u.toASCIIString());
            SecretKey key = EncryptionUtils.getSecretKey(this.proxyKeyPair.getPrivate(), serverKey,
                    Base64.getDecoder().decode(saltJwt.getJWTClaimsSet().getStringClaim("salt")));
            session.enableEncryption(key);
        } catch (ParseException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        ClientToServerHandshakePacket clientToServerHandshake = new ClientToServerHandshakePacket();
        session.sendPacketImmediately(clientToServerHandshake);
        return true;
    }

    @Override
    public boolean handle(ResourcePacksInfoPacket packet) {
        ResourcePackClientResponsePacket resourcePackClientResponse = new ResourcePackClientResponsePacket();
        resourcePackClientResponse.setStatus(Status.HAVE_ALL_PACKS);
        session.sendPacketImmediately(resourcePackClientResponse);
        return true;
    }

    @Override
    public boolean handle(ResourcePackStackPacket packet) {
        ResourcePackClientResponsePacket resourcePackClientResponse = new ResourcePackClientResponsePacket();
        resourcePackClientResponse.setStatus(Status.COMPLETED);
        session.sendPacketImmediately(resourcePackClientResponse);
        return true;
    }

    @Override
    public boolean handle(DisconnectPacket packet) {
        System.out.println("Disconnected, reason: " + packet.getKickMessage());
        return false;
    }

    @Override
    public boolean handle(TextPacket packet) {
        System.out.println("TextPacket");
        System.out.println(packet.getMessage());
        return false;
    }
}