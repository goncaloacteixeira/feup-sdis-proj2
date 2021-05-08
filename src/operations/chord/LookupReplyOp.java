package operations.chord;

import messages.chord.LookupReply;
import peer.Peer;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public class LookupReplyOp extends ChordOperation {
    public LookupReplyOp(SocketChannel channel, SSLEngine engine, LookupReply message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        log.debug("Starting lookup reply operation...");
    }
}
