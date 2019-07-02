package games.redpoint;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.flowpowered.math.vector.Vector3f;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.protocol.bedrock.BedrockClientSession;
import com.nukkitx.protocol.bedrock.data.CommandOriginData;
import com.nukkitx.protocol.bedrock.data.CommandOriginData.Origin;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.AddEntityPacket;
import com.nukkitx.protocol.bedrock.packet.AddPlayerPacket;
import com.nukkitx.protocol.bedrock.packet.ClientToServerHandshakePacket;
import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;
import com.nukkitx.protocol.bedrock.packet.CommandRequestPacket;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.packet.MovePlayerPacket;
import com.nukkitx.protocol.bedrock.packet.NetworkStackLatencyPacket;
import com.nukkitx.protocol.bedrock.packet.PlayerListPacket;
import com.nukkitx.protocol.bedrock.packet.RequestChunkRadiusPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackStackPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePacksInfoPacket;
import com.nukkitx.protocol.bedrock.packet.RespawnPacket;
import com.nukkitx.protocol.bedrock.packet.ServerToClientHandshakePacket;
import com.nukkitx.protocol.bedrock.packet.TextPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackClientResponsePacket.Status;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;

public class PapyrusBot implements BedrockPacketHandler {
    public final BedrockClientSession session;
    private final KeyPair proxyKeyPair;
    private final HashMap<String, PlayerListPacket.Entry> players;
    private String currentFocusedPlayer;
    private final HashMap<String, Vector3f> knownPlayerPositions;
    private State currentState;
    private final HashMap<Long, String> runtimePlayerLookup;
    private final HashMap<String, Long> lastUpdatedTime;
    private int teleportTimeout = 0;
    public UUID botUuid;
    private boolean seenBot;
    private CommandManager gameModeCommand;
    private CommandManager invisibilityCommand;
    private boolean updateCommands;

    private enum State {
        WAITING_FOR_OUT_OF_DATE_PLAYER, SEND_TELEPORT, WAIT_FOR_POSITION,
    }

    public PapyrusBot(BedrockClientSession session, KeyPair proxyKeyPair) {
        this.session = session;
        this.proxyKeyPair = proxyKeyPair;
        this.players = new HashMap<String, PlayerListPacket.Entry>();
        this.currentFocusedPlayer = null;
        this.knownPlayerPositions = new HashMap<String, Vector3f>();
        this.runtimePlayerLookup = new HashMap<Long, String>();
        this.currentState = State.WAITING_FOR_OUT_OF_DATE_PLAYER;
        this.teleportTimeout = 0;
        this.botUuid = new UUID(0, 0);
        this.seenBot = false;
        this.updateCommands = false;
        this.lastUpdatedTime = new HashMap<String, Long>();

        this.gameModeCommand = new CommandManager("gamemode creative @s");
        this.invisibilityCommand = new CommandManager("effect @s invisibility 99999 255 true");
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
    public boolean handle(NetworkStackLatencyPacket packet) {
        if (packet.isSendBack()) {
            session.sendPacketImmediately(packet);
        }
        return true;
    }

    @Override
    public boolean handle(DisconnectPacket packet) {
        System.out.println("Disconnected, reason: " + packet.getKickMessage());
        return false;
    }

    @Override
    public boolean handle(AddEntityPacket packet) {
        System.out.println("Add entity: " + packet.getIdentifier());
        return false;
    }

    @Override
    public boolean handle(MovePlayerPacket packet) {
        if (!this.runtimePlayerLookup.containsKey(packet.getRuntimeEntityId())) {
            return false;
        }

        String playerUuid = this.runtimePlayerLookup.get(packet.getRuntimeEntityId());
        if (!this.players.containsKey(playerUuid)) {
            return false;
        }

        this.knownPlayerPositions.put(playerUuid, packet.getPosition());
        this.lastUpdatedTime.put(playerUuid, System.currentTimeMillis());
        System.out.println("Move player: " + packet.toString());

        if (this.currentFocusedPlayer.equals(playerUuid)) {
            this.currentState = State.WAITING_FOR_OUT_OF_DATE_PLAYER;
        }

        return false;
    }

    @Override
    public boolean handle(AddPlayerPacket packet) {
        System.out.println("Add player: " + packet.toString());
        this.runtimePlayerLookup.put(packet.getRuntimeEntityId(), packet.getUuid().toString());

        this.knownPlayerPositions.put(packet.getUuid().toString(), packet.getPosition());
        this.lastUpdatedTime.put(packet.getUuid().toString(), System.currentTimeMillis());

        if (packet.getUuid().toString().equals(this.currentFocusedPlayer)) {
            this.currentState = State.WAITING_FOR_OUT_OF_DATE_PLAYER;
        }

        return false;
    }

    @Override
    public boolean handle(RespawnPacket packet) {

        System.out.println("Respawn packet: " + packet.toString());
        RequestChunkRadiusPacket packe2t = new RequestChunkRadiusPacket();
        packe2t.setRadius(64);
        session.sendPacketImmediately(packe2t);

        this.updateCommands = true;

        return false;
    }

    @Override
    public boolean handle(PlayerListPacket packet) {
        if (packet.getType() == PlayerListPacket.Type.ADD) {
            for (PlayerListPacket.Entry entry : packet.getEntries()) {
                if (entry.getName().equals("Papyrus")) {
                    this.botUuid = entry.getUuid();
                    this.seenBot = true;
                }

                this.players.put(entry.getUuid().toString(), entry);
            }
        } else if (packet.getType() == PlayerListPacket.Type.REMOVE) {
            for (PlayerListPacket.Entry entry : packet.getEntries()) {
                this.players.remove(entry.getUuid().toString());
            }
        }

        return false;
    }

    @Override
    public boolean handle(CommandOutputPacket packet) {
        this.gameModeCommand.onCommandOutputReceived(packet);
        this.invisibilityCommand.onCommandOutputReceived(packet);

        return false;
    }

    @Override
    public boolean handle(TextPacket packet) {
        System.out.println("TextPacket");
        System.out.println(packet.getMessage());
        return false;
    }

    public void update() {
        if (!this.seenBot) {
            return;
        }

        if (this.updateCommands) {
            this.gameModeCommand.update(this);
            this.invisibilityCommand.update(this);
        }

        if (!this.gameModeCommand.isSuccess || !this.invisibilityCommand.isSuccess) {
            return;
        }

        if (this.currentState == State.WAITING_FOR_OUT_OF_DATE_PLAYER) {
            // Find the first player that is out of date.
            long now = System.currentTimeMillis();

            for (PlayerListPacket.Entry entry : this.players.values()) {
                String u = entry.getUuid().toString();

                if (u.equals(this.botUuid.toString())) {
                    // Exclude bot
                    continue;
                }

                if (!this.lastUpdatedTime.containsKey(u)) {
                    System.out.println("Need to update known location of " + entry.getName() + ", no known location");

                    this.currentFocusedPlayer = u;
                    this.currentState = State.SEND_TELEPORT;
                    break;
                }

                // older than 60 seconds?
                if (this.lastUpdatedTime.get(u) + (60 * 1000) < now) {
                    System.out.println("Need to update known location of " + entry.getName() + ", location too old");

                    this.currentFocusedPlayer = u;
                    this.currentState = State.SEND_TELEPORT;
                    break;
                }
            }
        }

        if (this.currentFocusedPlayer == null) {
            return;
        }

        switch (this.currentState) {
        case WAITING_FOR_OUT_OF_DATE_PLAYER:
            // already handled above
            break;
        case SEND_TELEPORT:
            this.currentState = State.WAIT_FOR_POSITION;

            PlayerListPacket.Entry playerEntry = this.players.get(this.currentFocusedPlayer);
            System.out.println("Requesting teleport to " + playerEntry.getName() + "...");
            /*
             * TextPacket packet = new TextPacket(); packet.setMessage("/teleport @s \"" +
             * playerEntry.getName() + "\""); packet.setNeedsTranslation(false);
             * packet.setParameters(new ArrayList<String>());
             * packet.setSourceName("Papyrus"); packet.setType(TextPacket.Type.CHAT);
             * packet.setPlatformChatId(""); packet.setXuid("");
             * session.sendPacketImmediately(packet);
             * 
             * packet = new TextPacket(); packet.setMessage("/me");
             * packet.setNeedsTranslation(false); packet.setParameters(new
             * ArrayList<String>()); packet.setSourceName("Papyrus");
             * packet.setType(TextPacket.Type.CHAT); packet.setPlatformChatId("");
             * packet.setXuid(""); session.sendPacketImmediately(packet);
             */

            CommandRequestPacket cmdPacket = new CommandRequestPacket();
            cmdPacket.setCommand("execute \"" + playerEntry.getName() + "\" ~ ~ ~ /tp Papyrus ~ ~ ~");
            cmdPacket.setCommandOriginData(new CommandOriginData(Origin.PLAYER, playerEntry.getUuid(), "teleport", 0L));
            session.sendPacketImmediately(cmdPacket);

            /*
             * cmdPacket = new CommandRequestPacket(); cmdPacket.setCommand("/me");
             * cmdPacket.setCommandOriginData( new CommandOriginData(Origin.PLAYER,
             * this.botUuid, UUID.randomUUID().toString(), 0L));
             * session.sendPacketImmediately(cmdPacket);
             */

            break;
        case WAIT_FOR_POSITION:
            // do nothing; wait for packet
            this.teleportTimeout++;
            if (this.teleportTimeout > 60 * 10) {
                // retry
                System.out.println("Timeout on teleport");
                this.teleportTimeout = 0;
                this.currentState = State.SEND_TELEPORT;
            }
            break;
        }
    }
}