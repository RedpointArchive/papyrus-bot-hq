package games.redpoint.commands;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import games.redpoint.PapyrusBot;

public class FactoryCommandNode implements CommandNode {
    private CommandNode currentNode;
    private Factory factory;

    public interface Factory {
        CommandNode create(StatefulCommandGraph graph, PapyrusBot bot);
    }

    public FactoryCommandNode(Factory factory) {
        this.factory = factory;
    }

    @Override
    public void reset() {
        this.currentNode = null;
    }

    @Override
    public void update(StatefulCommandGraph graph, PapyrusBot bot) {
        if (this.currentNode == null) {
            this.currentNode = this.factory.create(graph, bot);
        }
        this.currentNode.update(graph, bot);
    }

    @Override
    public void onCommandOutputReceived(StatefulCommandGraph graph, PapyrusBot bot, CommandOutputPacket packet) {
        if (this.currentNode == null) {
            this.currentNode = this.factory.create(graph, bot);
        }
        this.currentNode.onCommandOutputReceived(graph, bot, packet);
    }

    @Override
    public CommandNodeState getState() {
        if (this.currentNode == null) {
            return CommandNodeState.PENDING;
        }
        return this.currentNode.getState();
    }

}