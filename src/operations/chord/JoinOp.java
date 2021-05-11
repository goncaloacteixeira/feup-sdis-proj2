package operations.chord;

import messages.Message;
import messages.chord.ChordMessage;
import messages.chord.Guid;
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

        Message message = new Guid(context.getReference(), String.valueOf(guid).getBytes(StandardCharsets.UTF_8));

        context.send(connection, message);
    }
}
