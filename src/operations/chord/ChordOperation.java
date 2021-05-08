package operations.chord;

import messages.chord.ChordMessage;
import messages.chord.Guid;
import messages.chord.Join;
import operations.Operation;
import peer.Peer;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public abstract class ChordOperation extends Operation {
    protected final ChordMessage message;
    protected final Peer context;

    public ChordOperation(SocketChannel channel, SSLEngine engine, ChordMessage message, Peer context) {
        super(channel, engine);
        this.message = message;
        this.context = context;
    }

    public static ChordOperation parse(ChordMessage message, Peer context, SocketChannel channel, SSLEngine engine) {
        switch (message.getOperation()) {
            case "JOIN":
                return new JoinOp(channel, engine, message, context);
            case "GUID":
                return new GuidOp(channel, engine, message, context);
            default:
                return null;
        }
    }
}
