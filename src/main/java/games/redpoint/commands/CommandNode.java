package games.redpoint.commands;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import games.redpoint.PapyrusBot;

public abstract interface CommandNode {
    public void reset();

    public void update(StatefulCommandGraph graph, PapyrusBot bot);

    public void onCommandOutputReceived(StatefulCommandGraph graph, PapyrusBot bot, CommandOutputPacket packet);

    public CommandNodeState getState();

    public enum CommandNodeState {
        PENDING, FAILED, SUCCESS
    }
}