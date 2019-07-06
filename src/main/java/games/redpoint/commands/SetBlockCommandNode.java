package games.redpoint.commands;

import java.util.UUID;

import com.flowpowered.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.data.CommandOriginData;
import com.nukkitx.protocol.bedrock.data.CommandOutputMessage;
import com.nukkitx.protocol.bedrock.data.CommandOriginData.Origin;
import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;
import com.nukkitx.protocol.bedrock.packet.CommandRequestPacket;

import org.apache.log4j.Logger;

import games.redpoint.PapyrusBot;

public class SetBlockCommandNode implements CommandNode {
    private static final Logger LOG = Logger.getLogger(SetBlockCommandNode.class);

    private String command;
    private UUID currentCommandId;
    private boolean hasCurrentCommand;
    public boolean isSuccess = false;
    public boolean isDone = false;

    public SetBlockCommandNode(Vector3i pos, String block) {
        this.command = "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block + " 0 replace";
        this.currentCommandId = UUID.randomUUID();
        this.hasCurrentCommand = false;
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
            shouldSend = !this.isSuccess;
        }

        if (shouldSend) {
            currentCommandId = UUID.randomUUID();

            LOG.info("sending: " + command);

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
            for (CommandOutputMessage message : packet.getMessages()) {
                if (message.getMessageId().equals("commands.setblock.noChange")) {
                    LOG.warn("command success: " + command);
                    this.isSuccess = true;
                }
            }

            if (!this.isSuccess) {
                if (packet.getSuccessCount() > 0) {
                    LOG.warn("command success: " + command);
                    this.isSuccess = true;
                } else {
                    LOG.warn("command failed: " + command + ", " + packet.toString());
                }
            }
        }
    }

    @Override
    public CommandNodeState getState() {
        if (!this.isDone) {
            return CommandNodeState.PENDING;
        }
        if (this.isSuccess) {
            return CommandNodeState.SUCCESS;
        } else {
            return CommandNodeState.PENDING;
        }
    }
}