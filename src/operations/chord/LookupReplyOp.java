package operations.chord;

import messages.chord.LookupReply;
import peer.Peer;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public class LookupReplyOp extends ChordOperation {
    public LookupReplyOp(SSLConnection connection, LookupReply message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        log.debug("Starting lookup reply operation...");
    }
}
