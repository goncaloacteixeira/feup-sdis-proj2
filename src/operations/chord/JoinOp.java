package operations.chord;

import messages.Message;
import messages.chord.ChordMessage;
import messages.chord.Guid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class JoinOp extends ChordOperation {
    private static final Logger log = LogManager.getLogger(JoinOp.class);

    public JoinOp(SocketChannel channel, SSLEngine engine, ChordMessage message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        log.debug("Started JOIN operation message...");

        int guid = context.generateNewKey();

        Message message = new Guid(context.getReference(), String.valueOf(guid).getBytes(StandardCharsets.UTF_8));

        try {
            context.write(this.channel, this.engine, message.encode());
        } catch (IOException e) {
            log.error("Error writing: " + e);
        }
    }
}
