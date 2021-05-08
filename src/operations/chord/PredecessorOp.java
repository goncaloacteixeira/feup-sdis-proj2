package operations.chord;

import messages.Message;
import messages.chord.ChordMessage;
import messages.chord.Predecessor;
import messages.chord.PredecessorReply;
import peer.Peer;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class PredecessorOp extends ChordOperation {
    public PredecessorOp(SocketChannel channel, SSLEngine engine, Predecessor message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        log.debug("Sending back predecessor...");

        String predecessor = "nil";
        if (context.getPredecessor() != null) {
            predecessor = context.getPredecessor().toString();
        }

        Message message = new PredecessorReply(context.getReference(), predecessor.getBytes(StandardCharsets.UTF_8));
        try {
            context.write(channel, engine, message.encode());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
