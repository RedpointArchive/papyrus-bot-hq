package games.redpoint;

import java.net.URI;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.protocol.bedrock.BedrockClientSession;
import com.nukkitx.protocol.bedrock.data.ItemData;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.AddEntityPacket;
import com.nukkitx.protocol.bedrock.packet.AddPlayerPacket;
import com.nukkitx.protocol.bedrock.packet.ClientToServerHandshakePacket;
import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.packet.InteractPacket;
import com.nukkitx.protocol.bedrock.packet.InventoryTransactionPacket;
import com.nukkitx.protocol.bedrock.packet.MovePlayerPacket;
import com.nukkitx.protocol.bedrock.packet.NetworkStackLatencyPacket;
import com.nukkitx.protocol.bedrock.packet.PlayerActionPacket;
import com.nukkitx.protocol.bedrock.packet.PlayerListPacket;
import com.nukkitx.protocol.bedrock.packet.RequestChunkRadiusPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackClientResponsePacket.Status;
import com.nukkitx.protocol.bedrock.packet.ResourcePackStackPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePacksInfoPacket;
import com.nukkitx.protocol.bedrock.packet.RespawnPacket;
import com.nukkitx.protocol.bedrock.packet.ServerToClientHandshakePacket;
import com.nukkitx.protocol.bedrock.packet.SetEntityDataPacket;
import com.nukkitx.protocol.bedrock.packet.StartGamePacket;
import com.nukkitx.protocol.bedrock.packet.TextPacket;
import com.nukkitx.protocol.bedrock.packet.UpdateBlockPacket;
import com.nukkitx.protocol.bedrock.packet.InventoryTransactionPacket.Type;
import com.nukkitx.protocol.bedrock.packet.PlayerActionPacket.Action;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;

import org.apache.log4j.Logger;

import games.redpoint.commands.CommandNode.CommandNodeState;
import games.redpoint.commands.ConditionalCommandNode;
import games.redpoint.commands.DelegatingCommandNode;
import games.redpoint.commands.ExecuteCommandNode;
import games.redpoint.commands.ExecuteCommandNode.RetryPolicy;
import games.redpoint.commands.FactoryCommandNode;
import games.redpoint.commands.ParallelCommandNode;
import games.redpoint.commands.ParallelCommandNode.ParallelSuccessState;
import games.redpoint.commands.SequentialCommandNode;
import games.redpoint.commands.SetBlockCommandNode;
import games.redpoint.commands.StateChangeCommandNode;
import games.redpoint.commands.StatefulCommandGraph;

public class PapyrusBot implements BedrockPacketHandler {
    private static final Logger LOG = Logger.getLogger(PapyrusBot.class);

    public final BedrockClientSession session;
    private final KeyPair proxyKeyPair;
    public final HashMap<String, PlayerListPacket.Entry> players;
    private String currentFocusedPlayer;
    public final HashMap<String, Vector3f> knownPlayerPositions;
    private final HashMap<Long, String> runtimePlayerLookup;
    private final HashMap<String, Long> lastUpdatedTime;
    public UUID botUuid;
    private boolean seenBot;
    private BotWebSocketServer webSocketServer;
    public ObjectMapper objectMapper;
    private boolean updateCommandGraph;
    private StatefulCommandGraph commandGraph;
    private long botRuntimeEntityId = 0;
    private String oldState = null;
    private boolean wasLastSleeping = false;

    public PapyrusBot(BedrockClientSession session, KeyPair proxyKeyPair) throws UnknownHostException {
        this.session = session;
        this.proxyKeyPair = proxyKeyPair;
        this.players = new HashMap<String, PlayerListPacket.Entry>();
        this.currentFocusedPlayer = null;
        this.knownPlayerPositions = new HashMap<String, Vector3f>();
        this.runtimePlayerLookup = new HashMap<Long, String>();
        this.botUuid = new UUID(0, 0);
        this.seenBot = false;
        this.lastUpdatedTime = new HashMap<String, Long>();
        this.webSocketServer = new BotWebSocketServer(this);
        this.objectMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        this.updateCommandGraph = false;
        this.commandGraph = new StatefulCommandGraph();

        this.commandGraph.add("init",
                new SequentialCommandNode()
                        .add(new ParallelCommandNode(ParallelSuccessState.ALL_SUCCESS)
                                .add(new ExecuteCommandNode("gamemode creative @s", RetryPolicy.ALWAYS_RETRY))
                                .add(new ExecuteCommandNode("effect @s invisibility 99999 255 true",
                                        RetryPolicy.ALWAYS_RETRY)))
                        .add(new ExecuteCommandNode("scoreboard objectives add dimension dummy \"Current Dimension\"",
                                RetryPolicy.IGNORE_ERRORS))
                        .add(new StateChangeCommandNode("setup-bed")));

        this.commandGraph.add("setup-bed",
                new SequentialCommandNode().add(new SetBlockCommandNode(new Vector3i(200, 0, 200), "air"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 0, 199), "air"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 0, 200), "air"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 0, 199), "air"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 1, 200), "air"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 1, 199), "air"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 1, 200), "air"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 1, 199), "air"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 2, 200), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 2, 199), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 2, 200), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 2, 199), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(199, 0, 200), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(199, 0, 199), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(202, 0, 200), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(202, 0, 199), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 0, 201), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 0, 198), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 0, 201), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 0, 198), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(199, 1, 200), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(199, 1, 199), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(202, 1, 200), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(202, 1, 199), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 1, 201), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 1, 198), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 1, 201), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(201, 1, 198), "bedrock"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 0, 200), "bed"))
                        .add(new SetBlockCommandNode(new Vector3i(200, 1, 199), "torch"))
                        .add(new DelegatingCommandNode((StatefulCommandGraph graph, PapyrusBot bot) -> {
                            if (this.botRuntimeEntityId == 0) {
                                return CommandNodeState.PENDING;
                            }
                            return CommandNodeState.SUCCESS;
                        })).add(new StateChangeCommandNode("waiting-for-out-of-date-player")));

        this.commandGraph.add("sleep",
                new SequentialCommandNode()
                        .add(new ExecuteCommandNode("tp @s 201 0 199 90 45", RetryPolicy.ALWAYS_RETRY))
                        .add(new DelegatingCommandNode((StatefulCommandGraph graph, PapyrusBot bot) -> {

                            LOG.info("Sending inventory transaction packet");
                            InventoryTransactionPacket invPacket = new InventoryTransactionPacket();
                            invPacket.setTransactionType(Type.ITEM_USE);
                            invPacket.setActionType(0);
                            invPacket.setBlockPosition(new Vector3i(200, 0, 200));
                            invPacket.setFace(0);
                            invPacket.setHotbarSlot(0);
                            invPacket.setItemInHand(ItemData.AIR);
                            invPacket.setPlayerPosition(new Vector3f(201, 0, 199));
                            invPacket.setClickPosition(new Vector3f(0.5, 0.5, 0.5));
                            invPacket.setRuntimeEntityId(this.botRuntimeEntityId);
                            session.sendPacket(invPacket);
                            /*
                             * LOG.info("Sending player action packet"); PlayerActionPacket packet = new
                             * PlayerActionPacket(); packet.setRuntimeEntityId(this.botRuntimeEntityId);
                             * packet.setBlockPosition(new Vector3i(200, 0, 200)); packet.setFace(0);
                             * packet.setAction(Action.START_SLEEP); session.sendPacket(packet);
                             */

                            this.wasLastSleeping = true;

                            return CommandNodeState.SUCCESS;
                        })).add(new StateChangeCommandNode("waiting-for-out-of-date-player")));

        this.commandGraph.add("waiting-for-out-of-date-player",
                new ConditionalCommandNode(new DelegatingCommandNode((StatefulCommandGraph graph, PapyrusBot bot) -> {
                    // Find the first player that is out of date.
                    long now = System.currentTimeMillis();

                    for (PlayerListPacket.Entry entry : this.players.values()) {
                        String u = entry.getUuid().toString();

                        if (u.equals(this.botUuid.toString())) {
                            // Exclude bot
                            continue;
                        }

                        if (!this.lastUpdatedTime.containsKey(u)) {
                            LOG.info("Need to update known location of " + entry.getName() + ", no known location");

                            this.currentFocusedPlayer = u;
                            this.wasLastSleeping = false;
                            return CommandNodeState.SUCCESS;
                        }

                        // older than 60 seconds?
                        if (this.lastUpdatedTime.get(u) + (60 * 1000) < now) {
                            LOG.info("Need to update known location of " + entry.getName() + ", location too old");

                            this.currentFocusedPlayer = u;
                            this.wasLastSleeping = false;
                            return CommandNodeState.SUCCESS;
                        }
                    }

                    if (this.wasLastSleeping) {
                        InventoryTransactionPacket invPacket = new InventoryTransactionPacket();
                        invPacket.setTransactionType(Type.ITEM_USE);
                        invPacket.setActionType(0);
                        invPacket.setBlockPosition(new Vector3i(200, 0, 200));
                        invPacket.setFace(0);
                        invPacket.setHotbarSlot(0);
                        invPacket.setItemInHand(ItemData.AIR);
                        invPacket.setPlayerPosition(new Vector3f(201, 0, 199));
                        invPacket.setClickPosition(new Vector3f(0.5, 0.5, 0.5));
                        invPacket.setRuntimeEntityId(this.botRuntimeEntityId);
                        session.sendPacket(invPacket);

                        return CommandNodeState.PENDING;
                    } else {
                        return CommandNodeState.FAILED;
                    }
                })).onSuccess(new StateChangeCommandNode("check-dimension"))
                        .onFailed(new StateChangeCommandNode("sleep")));

        this.commandGraph.add("check-dimension",
                new FactoryCommandNode((StatefulCommandGraph graph, PapyrusBot bot) -> {
                    PlayerListPacket.Entry playerEntry = this.players.get(this.currentFocusedPlayer);
                    String name = playerEntry.getName();
                    return new SequentialCommandNode()
                            .add(new ExecuteCommandNode(
                                    "execute \"" + name + "\" ~ ~ ~ detect 0 0 0 bedrock 0 /scoreboard players set \""
                                            + name + "\" dimension 0",
                                    RetryPolicy.ALWAYS_RETRY))
                            .add(new ExecuteCommandNode(
                                    "execute \"" + name + "\" ~ ~ ~ detect 0 127 0 bedrock 0 /scoreboard players set \""
                                            + name + "\" dimension 1",
                                    RetryPolicy.IGNORE_ERRORS))
                            .add(new ConditionalCommandNode(new ExecuteCommandNode(
                                    "scoreboard players test \"" + name + "\" dimension 0 0", RetryPolicy.NO_RETRY))
                                            .onSuccess(new StateChangeCommandNode("teleport"))
                                            .onFailed(new SequentialCommandNode().add(new DelegatingCommandNode(
                                                    (StatefulCommandGraph graph1, PapyrusBot bot1) -> {
                                                        // we can't teleport to this player because they're in the
                                                        // nether
                                                        this.knownPlayerPositions.put(playerEntry.getUuid().toString(),
                                                                new Vector3f(0, 0, 0));
                                                        this.lastUpdatedTime.put(playerEntry.getUuid().toString(),
                                                                System.currentTimeMillis());
                                                        return CommandNodeState.SUCCESS;

                                                    })).add(new StateChangeCommandNode(
                                                            "waiting-for-out-of-date-player"))));
                }));

        this.commandGraph.add("teleport", new FactoryCommandNode((StatefulCommandGraph graph, PapyrusBot bot) -> {
            PlayerListPacket.Entry playerEntry = this.players.get(this.currentFocusedPlayer);
            LOG.warn("Requesting teleport to " + playerEntry.getName() + "...");
            return new SequentialCommandNode().add(new ExecuteCommandNode(
                    "execute \"" + playerEntry.getName() + "\" ~ ~ ~ detect 0 0 0 bedrock 0 tp Papyrus ~ ~ ~",
                    RetryPolicy.ALWAYS_RETRY));
        }));

        this.commandGraph.setState("init");

        this.webSocketServer.start();
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
    public boolean handle(PlayerActionPacket packet) {
        LOG.warn(packet.toString());
        if (packet.getAction() == Action.START_SLEEP) {
            // someone is sleeping, start sleeping
            LOG.warn("switching to sleep...");
            this.oldState = this.commandGraph.getState();
            this.commandGraph.setState("sleep");
        }
        if (packet.getAction() == Action.STOP_SLEEP && this.oldState != null) {
            LOG.warn("stopping sleep...");
            // someone stopped sleeping, stop sleeping
            this.commandGraph.setState(this.oldState);
        }
        return false;
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
        LOG.warn("Disconnected, reason: " + packet.getKickMessage());
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

        PlayerPositionWebSocketMessage msg = new PlayerPositionWebSocketMessage();
        msg.playerName = this.players.get(playerUuid).getName();
        msg.x = packet.getPosition().getX();
        msg.z = packet.getPosition().getZ();
        try {
            this.webSocketServer.broadcast(this.objectMapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            LOG.error(e.toString());
        }

        if (playerUuid.equals(this.currentFocusedPlayer)) {
            this.commandGraph.setState("waiting-for-out-of-date-player");
        }

        return false;
    }

    @Override
    public boolean handle(StartGamePacket packet) {
        this.botRuntimeEntityId = packet.getRuntimeEntityId();
        return false;
    }

    @Override
    public boolean handle(AddPlayerPacket packet) {
        this.runtimePlayerLookup.put(packet.getRuntimeEntityId(), packet.getUuid().toString());

        this.knownPlayerPositions.put(packet.getUuid().toString(), packet.getPosition());
        this.lastUpdatedTime.put(packet.getUuid().toString(), System.currentTimeMillis());

        PlayerPositionWebSocketMessage msg = new PlayerPositionWebSocketMessage();
        msg.playerName = this.players.get(packet.getUuid().toString()).getName();
        msg.x = packet.getPosition().getX();
        msg.z = packet.getPosition().getZ();
        try {
            this.webSocketServer.broadcast(this.objectMapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            LOG.error(e.toString());
        }

        if (packet.getUuid().toString().equals(this.currentFocusedPlayer)) {
            this.commandGraph.setState("waiting-for-out-of-date-player");
        }

        return false;
    }

    @Override
    public boolean handle(RespawnPacket packet) {

        LOG.warn("Papyrus bot is now connected and in the game");
        RequestChunkRadiusPacket packe2t = new RequestChunkRadiusPacket();
        packe2t.setRadius(64);
        session.sendPacketImmediately(packe2t);

        this.updateCommandGraph = true;

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
        if (this.updateCommandGraph) {
            this.commandGraph.onCommandOutputReceived(this, packet);
        }
        return false;
    }

    @Override
    public boolean handle(TextPacket packet) {
        PlayerChatWebSocketMessage msg = new PlayerChatWebSocketMessage();
        if (packet.getSourceName() == null) {
            // system message, ignore
            return false;
        }
        msg.playerName = packet.getSourceName();
        msg.message = packet.getMessage();
        try {
            this.webSocketServer.broadcast(this.objectMapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            LOG.error(e.toString());
        }

        return false;
    }

    public void update() {
        if (!this.seenBot) {
            return;
        }

        if (this.updateCommandGraph) {
            this.commandGraph.update(this);
        }
    }
}