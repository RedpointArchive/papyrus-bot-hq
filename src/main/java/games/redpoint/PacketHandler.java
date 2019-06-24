package games.redpoint;

import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import com.nukkitx.protocol.bedrock.packet.BiomeDefinitionListPacket;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.packet.InventoryContentPacket;
import com.nukkitx.protocol.bedrock.packet.LoginPacket;
import com.nukkitx.protocol.bedrock.packet.ServerToClientHandshakePacket;
import com.nukkitx.protocol.bedrock.packet.StartGamePacket;
import com.nukkitx.protocol.bedrock.packet.TextPacket;

public class PacketHandler implements BedrockPacketHandler 
{
    public boolean handle(ServerToClientHandshakePacket packet) {
        System.out.println("servertoclienthandshake");
        return false;
    }

    public boolean handle(AvailableEntityIdentifiersPacket packet) {
        System.out.println("AvailableEntityIdentifiersPacket");
        return false;
    }

    public boolean handle(BiomeDefinitionListPacket packet) {
        System.out.println("BiomeDefinitionListPacket");
        return false;
    }

    public boolean handle(StartGamePacket packet) {
        System.out.println("StartGamePacket");
        return false;
    }

    @Override
    public boolean handle(DisconnectPacket packet) {
        System.out.println("Disconnected, reason: " + packet.getKickMessage());
        return false;
    }

    @Override
    public boolean handle(InventoryContentPacket packet) {
        System.out.println("InventoryContentPacket");
        return false;
    }

    @Override
    public boolean handle(TextPacket packet) {
        System.out.println("TextPacket");
        System.out.println(packet.getMessage());
        return false;
    }

    @Override
    public boolean handle(LoginPacket packet) {
        System.out.println(packet.getChainData().toString());
        System.out.println(packet.getSkinData().toString());

        return false;
    }
}