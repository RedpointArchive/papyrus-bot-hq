package games.redpoint;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.flowpowered.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.packet.PlayerListPacket;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class BotWebSocketServer extends WebSocketServer {
    private PapyrusBot bot;

    public BotWebSocketServer(PapyrusBot bot) throws UnknownHostException {
        super(new InetSocketAddress(8080));

        this.bot = bot;
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Got WebSocket close");
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("Got WebSocket exception: " + ex.toString());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Got WebSocket message: " + message);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Got WebSocket open");

        for (PlayerListPacket.Entry entry : this.bot.players.values()) {
            if (this.bot.knownPlayerPositions.containsKey(entry.getUuid().toString())) {
                Vector3f position = this.bot.knownPlayerPositions.get(entry.getUuid().toString());

                PlayerPositionWebSocketMessage msg = new PlayerPositionWebSocketMessage();
                msg.playerName = entry.getName();
                msg.x = position.getX();
                msg.z = position.getZ();
                try {
                    conn.send(this.bot.objectMapper.writeValueAsString(msg));
                } catch (JsonProcessingException e) {
                }
            }
        }
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server is listening on port 8080");
    }
}