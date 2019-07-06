package games.redpoint.commands;

import java.util.UUID;

import com.nukkitx.protocol.bedrock.data.CommandOriginData;
import com.nukkitx.protocol.bedrock.data.CommandOriginData.Origin;
import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;
import com.nukkitx.protocol.bedrock.packet.CommandRequestPacket;

import games.redpoint.PapyrusBot;

public class ExecuteCommandNode implements CommandNode {
    private String command;
    private UUID currentCommandId;
    private boolean hasCurrentCommand;
    public boolean isSuccess = false;
    public boolean isDone = false;
    public RetryPolicy policy = RetryPolicy.NO_RETRY;

    public enum RetryPolicy {
        NO_RETRY, ALWAYS_RETRY
    }

    public ExecuteCommandNode(String command, RetryPolicy policy) {
        this.command = command;
        this.currentCommandId = UUID.randomUUID();
        this.hasCurrentCommand = false;
        this.policy = policy;
    }

    @Override
    public void reset() {
        this.currentCommandId = UUID.randomUUID();
        this.hasCurrentCommand = false;
        this.isSuccess = false;
        this.isDone = false;
    }

    @Override
    public void update(StatefulCommandGraph graph, PapyrusBot bot) {
        boolean shouldSend = false;
        if (!this.hasCurrentCommand) {
            switch (this.policy) {
            case NO_RETRY:
                shouldSend = !this.isDone;
                break;
            case ALWAYS_RETRY:
                shouldSend = !this.isSuccess;
                break;
            }
        }

        if (shouldSend) {
            currentCommandId = UUID.randomUUID();

            CommandRequestPacket cmdPacket = new CommandRequestPacket();
            cmdPacket.setCommand(command);
            cmdPacket.setCommandOriginData(new CommandOriginData(Origin.PLAYER, currentCommandId, "", 0L));
            bot.session.sendPacketImmediately(cmdPacket);

            this.hasCurrentCommand = true;
        }
    }

    @Override
    public void onCommandOutputReceived(StatefulCommandGraph graph, PapyrusBot bot, CommandOutputPacket packet) {
        if (packet.getCommandOriginData().getUuid().toString().equals(currentCommandId.toString())) {
            this.isDone = true;
            this.hasCurrentCommand = false;
            if (packet.getSuccessCount() > 0) {
                System.out.println("successfully ran: " + command);
                this.isSuccess = true;
            }
        }
    }

    @Override
    public CommandNodeState getState() {
        if (!this.isDone) {
            return CommandNodeState.PENDING;
        }
        switch (this.policy) {
        case NO_RETRY:
            if (this.isSuccess) {
                return CommandNodeState.SUCCESS;
            } else {
                return CommandNodeState.FAILED;
            }
        case ALWAYS_RETRY:
            if (this.isSuccess) {
                return CommandNodeState.SUCCESS;
            } else {
                return CommandNodeState.PENDING;
            }
        }
        return CommandNodeState.PENDING;
    }
}