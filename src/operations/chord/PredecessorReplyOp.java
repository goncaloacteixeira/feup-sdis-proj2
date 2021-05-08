package operations.chord;

import messages.chord.ChordMessage;
import messages.chord.PredecessorReply;
import peer.Peer;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public class PredecessorReplyOp extends ChordOperation {
    public PredecessorReplyOp(SocketChannel channel, SSLEngine engine, PredecessorReply message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        // this will be handled by the chord peer
    }
}
