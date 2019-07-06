package games.redpoint.commands;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import games.redpoint.PapyrusBot;

public class ConditionalCommandNode implements CommandNode {
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
                    this.current = this.onDone;
                } else {
                    this.current = this.onSuccess;
                }
                break;
            case FAILED:
                if (this.onDone != null) {
                    this.current = this.onDone;
                } else {
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