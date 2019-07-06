package games.redpoint.commands;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import org.apache.log4j.Logger;

import games.redpoint.PapyrusBot;

public class SequentialCommandNode implements CommandNode {
    private static final Logger LOG = Logger.getLogger(ConditionalCommandNode.class);

    private CommandNodeState currentState;
    private DelegatingCommandNode doneOnFailedNode;
    private DelegatingCommandNode doneOnSuccessNode;
    private ConditionalCommandNode currentNode;
    private ConditionalCommandNode firstNode;

    public SequentialCommandNode() {
        this.currentState = CommandNodeState.PENDING;
        this.doneOnFailedNode = new DelegatingCommandNode((StatefulCommandGraph graph, PapyrusBot bot) -> {
            LOG.debug("sequential command node failed");
            this.currentState = CommandNodeState.FAILED;
            return this.currentState;
        });
        this.doneOnSuccessNode = new DelegatingCommandNode((StatefulCommandGraph graph, PapyrusBot bot) -> {
            LOG.debug("sequential command node success");
            this.currentState = CommandNodeState.SUCCESS;
            return this.currentState;
        });
    }

    public SequentialCommandNode add(CommandNode nextCommandNode) {
        ConditionalCommandNode nextConditionalNode = new ConditionalCommandNode(nextCommandNode)
                .onSuccess(this.doneOnSuccessNode).onFailed(this.doneOnFailedNode);

        if (this.currentNode != null) {
            this.currentNode.onSuccess(nextConditionalNode);
        } else {
            this.firstNode = nextConditionalNode;
        }

        this.currentNode = nextConditionalNode;
        return this;
    }

    @Override
    public void reset() {
        if (this.firstNode == null) {
            return;
        }

        this.firstNode.reset();
    }

    @Override
    public void update(StatefulCommandGraph graph, PapyrusBot bot) {
        if (this.firstNode == null) {
            return;
        }

        this.firstNode.update(graph, bot);
    }

    @Override
    public void onCommandOutputReceived(StatefulCommandGraph graph, PapyrusBot bot, CommandOutputPacket packet) {
        if (this.firstNode == null) {
            return;
        }

        this.firstNode.onCommandOutputReceived(graph, bot, packet);
    }

    @Override
    public CommandNodeState getState() {
        if (this.firstNode == null) {
            return CommandNodeState.SUCCESS;
        }

        return this.firstNode.getState();
    }
}