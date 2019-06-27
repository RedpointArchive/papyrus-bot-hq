package games.redpoint;

import java.util.Collection;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.protocol.bedrock.handler.BatchHandler;

import io.netty.buffer.ByteBuf;

public class PacketBatchHandler implements BatchHandler {
    private final PacketHandler packetHandler;

    public PacketBatchHandler(PacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    @Override
    public void handle(BedrockSession session, ByteBuf compressed, Collection<BedrockPacket> packets) {
        for (BedrockPacket packet : packets) {
            if (packet.handle(this.packetHandler)) {
               // System.out.println("HANDLED: " + packet.toString());
            } else {
               // System.out.println("UNHANDLED: " + packet.toString());
            }
        }
    }
}