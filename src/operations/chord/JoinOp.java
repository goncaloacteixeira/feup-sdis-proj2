package operations.chord;

import messages.Message;
import messages.chord.ChordMessage;
import messages.chord.Guid;
import peer.Peer;
import peer.chord.ChordPeer;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class JoinOp extends ChordOperation {
    public JoinOp(SocketChannel channel, SSLEngine engine, ChordMessage message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        log.debug("Started JOIN operation message...");

        int guid = ChordPeer.generateNewKey(new InetSocketAddress(channel.socket().getInetAddress(), channel.socket().getPort()));

        Message message = new Guid(context.getReference(), String.valueOf(guid).getBytes(StandardCharsets.UTF_8));

        try {
            context.write(this.channel, this.engine, message.encode());
        } catch (IOException e) {
            log.error("Error writing: " + e);
        }
    }
}
