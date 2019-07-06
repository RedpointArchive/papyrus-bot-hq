package games.redpoint.commands;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import org.apache.log4j.Logger;

import games.redpoint.PapyrusBot;

public class StateChangeCommandNode implements CommandNode {
    private static final Logger LOG = Logger.getLogger(StateChangeCommandNode.class);

    private String newStateName;

    public StateChangeCommandNode(String newStateName) {
        this.newStateName = newStateName;
    }

    @Override
    public void reset() {
        // no implementation
    }

    @Override
    public void update(StatefulCommandGraph graph, PapyrusBot bot) {
        LOG.info("changing state to: " + this.newStateName);
        graph.setState(this.newStateName);
    }

    @Override
    public void onCommandOutputReceived(StatefulCommandGraph graph, PapyrusBot bot, CommandOutputPacket packet) {
        // no implementation
    }

    @Override
    public CommandNodeState getState() {
        return CommandNodeState.PENDING;
    }

}