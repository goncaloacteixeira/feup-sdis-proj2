package operations.chord;

import messages.chord.*;
import operations.Operation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public abstract class ChordOperation extends Operation {
    protected final Logger log = LogManager.getLogger(getClass());

    protected final ChordMessage message;
    protected final Peer context;

    public ChordOperation(SSLConnection connection, ChordMessage message, Peer context) {
        super(connection);
        this.message = message;
        this.context = context;
    }

    public static ChordOperation parse(ChordMessage message, Peer context, SSLConnection connection) {
        switch (message.getOperation()) {
            case "JOIN":
                return new JoinOp(connection, message, context);
            case "GUID":
                return new GuidOp(connection, (Guid) message, context);
            case "LOOKUP":
                return new LookupOp(connection, (Lookup) message, context);
            case "LOOKUPREPLY":
                return new LookupReplyOp(connection, (LookupReply) message, context);
            case "PREDECESSOR":
                return new PredecessorOp(connection, (Predecessor) message, context);
            case "PREDECESSORREPLY":
                return new PredecessorReplyOp(connection, (PredecessorReply) message, context);
            case "NOTIFICATION":
                return new NotificationOp(connection, (Notification) message, context);
            default:
                return null;
        }
    }
}
