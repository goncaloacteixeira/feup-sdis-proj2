package operations.chord;

import messages.chord.*;
import operations.Operation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public abstract class ChordOperation extends Operation {
    protected final Logger log = LogManager.getLogger(getClass());

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
                return new GuidOp(channel, engine, (Guid) message, context);
            case "LOOKUP":
                return new LookupOp(channel, engine, (Lookup) message, context);
            case "LOOKUPREPLY":
                return new LookupReplyOp(channel, engine, (LookupReply) message, context);
            default:
                return null;
        }
    }
}
