package games.redpoint.commands;

import java.util.HashMap;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import org.apache.log4j.Logger;

import games.redpoint.PapyrusBot;

public class StatefulCommandGraph {
    private static final Logger LOG = Logger.getLogger(StateChangeCommandNode.class);

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
        LOG.debug("command graph is changing state to: " + name);
        this.currentNode = this.states.get(name);
        return this;
    }

    public void update(PapyrusBot bot) {
        if (this.currentNode == null) {
            LOG.error("command graph does not have a current state");
            return;
        }
        this.currentNode.update(this, bot);
    }

    public void onCommandOutputReceived(PapyrusBot bot, CommandOutputPacket packet) {
        if (this.currentNode == null) {
            LOG.error("command graph does not have a current state");
            return;
        }
        this.currentNode.onCommandOutputReceived(this, bot, packet);
    }
}