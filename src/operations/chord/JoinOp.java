package operations.chord;

import messages.Message;
import messages.chord.ChordMessage;
import messages.chord.Guid;
import peer.Constants;
import peer.Peer;
import peer.chord.ChordPeer;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class JoinOp extends ChordOperation {
    public JoinOp(SSLConnection connection, ChordMessage message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        log.debug("Started JOIN operation message...");

        int guid = ChordPeer.generateNewKey(new InetSocketAddress(connection.getSocketChannel().socket().getInetAddress(), connection.getSocketChannel().socket().getPort()));

        log.info("Looking for {} to check if it already exists...", guid);
        while (guid == context.findSuccessor(guid).getGuid()) {
            guid += 1;
            if (guid == Constants.CHORD_MAX_PEERS) guid = 0;
            log.info("Looking for {} to check if it already exists...", guid);
        }

        log.info("New Peer join the circle: {} on {}", guid, message.getSender().getAddress());
        Message message = new Guid(context.getReference(), String.valueOf(guid).getBytes(StandardCharsets.UTF_8));

        context.send(connection, message);
    }
}
