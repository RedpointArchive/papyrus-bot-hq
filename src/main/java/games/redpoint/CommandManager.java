package games.redpoint;

import java.util.UUID;

import com.nukkitx.protocol.bedrock.data.CommandOriginData;
import com.nukkitx.protocol.bedrock.data.CommandOriginData.Origin;
import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;
import com.nukkitx.protocol.bedrock.packet.CommandRequestPacket;

public class CommandManager {
    private String command;
    private UUID currentCommandId;
    private boolean hasCurrentCommand;
    public boolean isSuccess = false;

    public CommandManager(String command) {
        this.command = command;
        this.currentCommandId = UUID.randomUUID();
        this.hasCurrentCommand = false;
    }

    public void update(PapyrusBot bot) {
        if (!this.hasCurrentCommand && !this.isSuccess) {
            currentCommandId = UUID.randomUUID();

            CommandRequestPacket cmdPacket = new CommandRequestPacket();
            cmdPacket.setCommand(command);
            cmdPacket.setCommandOriginData(new CommandOriginData(Origin.PLAYER, currentCommandId, "", 0L));
            bot.session.sendPacketImmediately(cmdPacket);

            this.hasCurrentCommand = true;
        }
    }

    public void onCommandOutputReceived(CommandOutputPacket packet) {
        if (packet.getCommandOriginData().getUuid().toString().equals(currentCommandId.toString())) {
            if (packet.getSuccessCount() > 0) {
                System.out.println("successfully ran: " + command);
                this.isSuccess = true;
            } else {
                // try again
                this.hasCurrentCommand = false;
            }
        }
    }
}