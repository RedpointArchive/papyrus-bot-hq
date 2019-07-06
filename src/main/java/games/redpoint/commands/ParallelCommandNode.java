package games.redpoint.commands;

import java.util.ArrayList;
import java.util.List;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import games.redpoint.PapyrusBot;

public class ParallelCommandNode implements CommandNode {
    private List<CommandNode> commands = new ArrayList<CommandNode>();
    private ParallelSuccessState successState;

    public ParallelCommandNode(ParallelSuccessState successState) {
        this.successState = successState;
    }

    public enum ParallelSuccessState {
        ANY_SUCCESS, ALL_SUCCESS,
    }

    public ParallelCommandNode add(CommandNode command) {
        this.commands.add(command);
        return this;
    }

    @Override
    public void reset() {
        for (CommandNode command : this.commands) {
            command.reset();
        }
    }

    @Override
    public void update(StatefulCommandGraph graph, PapyrusBot bot) {
        for (CommandNode command : this.commands) {
            command.update(graph, bot);
        }
    }

    @Override
    public void onCommandOutputReceived(StatefulCommandGraph graph, PapyrusBot bot, CommandOutputPacket packet) {
        for (CommandNode command : this.commands) {
            command.onCommandOutputReceived(graph, bot, packet);
        }
    }

    @Override
    public CommandNodeState getState() {
        boolean allSuccess = true;
        boolean anySuccess = false;
        for (CommandNode command : this.commands) {
            CommandNodeState substate = command.getState();
            if (substate == CommandNodeState.PENDING) {
                return CommandNodeState.PENDING;
            }
            if (substate == CommandNodeState.SUCCESS) {
                anySuccess = true;
            }
            if (substate != CommandNodeState.SUCCESS) {
                allSuccess = false;
            }
        }
        if (this.successState == ParallelSuccessState.ANY_SUCCESS) {
            if (anySuccess) {
                return CommandNodeState.SUCCESS;
            } else {
                return CommandNodeState.FAILED;
            }
        } else {
            if (allSuccess) {
                return CommandNodeState.SUCCESS;
            } else {
                return CommandNodeState.FAILED;
            }
        }
    }
}