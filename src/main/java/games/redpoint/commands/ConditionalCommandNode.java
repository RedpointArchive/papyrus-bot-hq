package games.redpoint.commands;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import org.apache.log4j.Logger;

import games.redpoint.PapyrusBot;

public class ConditionalCommandNode implements CommandNode {
    private static final Logger LOG = Logger.getLogger(ConditionalCommandNode.class);

    private CommandNode condition;
    private CommandNode onFailed;
    private CommandNode onSuccess;
    private CommandNode onDone;
    private CommandNode current;

    public ConditionalCommandNode(CommandNode condition) {
        this.condition = condition;
        this.current = this.condition;
    }

    public ConditionalCommandNode onSuccess(CommandNode successNode) {
        this.onSuccess = successNode;
        return this;
    }

    public ConditionalCommandNode onFailed(CommandNode failedNode) {
        this.onFailed = failedNode;
        return this;
    }

    public ConditionalCommandNode onDone(CommandNode doneNode) {
        this.onSuccess = doneNode;
        return this;
    }

    @Override
    public void reset() {
        LOG.info("resetting to condition");
        this.current = this.condition;
        if (this.condition != null) {
            this.condition.reset();
        }
        if (this.onFailed != null) {
            this.onFailed.reset();
        }
        if (this.onSuccess != null) {
            this.onSuccess.reset();
        }
        if (this.onDone != null) {
            this.onDone.reset();
        }
    }

    @Override
    public void update(StatefulCommandGraph graph, PapyrusBot bot) {
        if (this.current != null) {
            this.current.update(graph, bot);
        }

        if (this.current == this.condition) {
            CommandNodeState state = this.current.getState();
            switch (state) {
            case PENDING:
                // do nothing
                break;
            case SUCCESS:
                if (this.onDone != null) {
                    LOG.info("on success: switching to done node");
                    this.current = this.onDone;
                } else {
                    LOG.info("on success: switching to success node");
                    this.current = this.onSuccess;
                }
                break;
            case FAILED:
                if (this.onDone != null) {
                    LOG.info("on failed: switching to done node");
                    this.current = this.onDone;
                } else {
                    LOG.info("on failed: switching to failed node");
                    this.current = this.onFailed;
                }
                break;
            }
        }
    }

    @Override
    public void onCommandOutputReceived(StatefulCommandGraph graph, PapyrusBot bot, CommandOutputPacket packet) {
        if (this.current != null) {
            this.current.onCommandOutputReceived(graph, bot, packet);
        }
    }

    @Override
    public CommandNodeState getState() {
        if (this.current == null) {
            return this.condition.getState();
        }

        return this.current.getState();
    }
}