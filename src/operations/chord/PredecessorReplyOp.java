package operations.chord;

import messages.chord.ChordMessage;
import messages.chord.PredecessorReply;
import peer.Peer;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public class PredecessorReplyOp extends ChordOperation {
    public PredecessorReplyOp(SSLConnection connection, PredecessorReply message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        // this will be handled by the chord peer
    }
}
