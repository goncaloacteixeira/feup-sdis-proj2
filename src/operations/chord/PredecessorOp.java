package operations.chord;

import messages.Message;
import messages.chord.Predecessor;
import messages.chord.PredecessorReply;
import peer.Peer;
import peer.ssl.SSLConnection;

import java.nio.charset.StandardCharsets;

public class PredecessorOp extends ChordOperation {
    public PredecessorOp(SSLConnection connection, Predecessor message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        log.debug("Sending back predecessor...");

        String predecessor = "nil";
        if (context.getPredecessor() != null) {
            predecessor = context.getPredecessor().toString();
        }

        Message message = new PredecessorReply(context.getReference(), predecessor.getBytes(StandardCharsets.UTF_8));

        context.send(connection, message);
    }
}
