package operations.chord;

import messages.chord.ChordMessage;
import messages.chord.Notification;
import peer.Peer;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.SocketChannel;

public class NotificationOp extends ChordOperation {
    public NotificationOp(SocketChannel channel, SSLEngine engine, Notification message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        ChordReference reference = ((Notification) message).getReference();

        if (context.getPredecessor() == null || ChordPeer.between(reference.getGuid(), context.getPredecessor().getGuid(), context.getGuid(), true)) {
            log.debug("Updated predecessor: " + reference);
            context.setPredecessor(reference);
        }

        try {
            context.closeConnection(new SSLConnection(channel, engine, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
