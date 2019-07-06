package games.redpoint.commands;

import java.util.HashMap;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import games.redpoint.PapyrusBot;

public class StatefulCommandGraph {
    private HashMap<String, CommandNode> states;
    private CommandNode currentNode;

    public StatefulCommandGraph() {
        this.states = new HashMap<String, CommandNode>();
        this.currentNode = null;
    }

    public StatefulCommandGraph add(String name, CommandNode node) {
        this.states.put(name, node);
        return this;
    }

    public StatefulCommandGraph setState(String name) {
        if (this.currentNode != null) {
            this.currentNode.reset();
        }
        this.currentNode = this.states.get(name);
        return this;
    }

    public void update(PapyrusBot bot) {
        this.currentNode.update(this, bot);
    }

    public void onCommandOutputReceived(PapyrusBot bot, CommandOutputPacket packet) {
        this.currentNode.onCommandOutputReceived(this, bot, packet);
    }
}