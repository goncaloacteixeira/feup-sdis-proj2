package operations.chord;

import messages.Message;
import messages.chord.ChordMessage;
import messages.chord.Guid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

public class JoinOp extends ChordOperation {
    private static final Logger log = LogManager.getLogger(JoinOp.class);

    public JoinOp(SocketChannel channel, SSLEngine engine, ChordMessage message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        log.debug("Started JOIN operation message...");

        int guid = ChordPeer.generateNewKey(new InetSocketAddress(channel.socket().getInetAddress(), channel.socket().getPort()));

        String body = String.format("%d\n::\n%s", guid, context.getRoutingTableString());

        Message message = new Guid(context.getReference(), body.getBytes(StandardCharsets.UTF_8));

        System.out.println(context.getReference());
        System.out.println(message);

        try {
            context.write(this.channel, this.engine, message.encode());
        } catch (IOException e) {
            log.error("Error writing: " + e);
        }
    }
}
