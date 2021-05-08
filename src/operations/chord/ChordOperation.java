package operations.chord;

import messages.chord.ChordMessage;
import operations.Operation;
import peer.Peer;

public abstract class ChordOperation extends Operation {
    protected final ChordMessage message;
    protected final Peer context;

    public ChordOperation(ChordMessage message, Peer context) {
        this.message = message;
        this.context = context;
    }

    public static ChordOperation parse(ChordMessage message, Peer context) {
        switch (message.getOperation()) {
            case "JOIN":
                return new JoinOp(message, context);
            default:
                return null;
        }
    }
}
