package games.redpoint.commands;

import com.nukkitx.protocol.bedrock.packet.CommandOutputPacket;

import games.redpoint.PapyrusBot;

public class DelegatingCommandNode implements CommandNode {
    private Delegate delegate;
    private CommandNodeState currentState = CommandNodeState.PENDING;

    public interface Delegate {
        CommandNodeState update(StatefulCommandGraph graph, PapyrusBot bot);
    }

    public DelegatingCommandNode(Delegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public void reset() {
        this.currentState = CommandNodeState.PENDING;
    }

    @Override
    public void update(StatefulCommandGraph graph, PapyrusBot bot) {
        if (this.currentState == CommandNodeState.PENDING) {
            this.currentState = this.delegate.update(graph, bot);
        }
    }

    @Override
    public void onCommandOutputReceived(StatefulCommandGraph graph, PapyrusBot bot, CommandOutputPacket packet) {
        // no implementation
    }

    @Override
    public CommandNodeState getState() {
        return this.currentState;
    }

}