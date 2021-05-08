package operations.chord;

import messages.chord.Guid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public class GuidOp extends ChordOperation {
    private static final Logger log = LogManager.getLogger(JoinOp.class);

    public GuidOp(SocketChannel channel, SSLEngine engine, Guid message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        log.debug("Started GUID operation...");
        context.setGuid(((Guid) message).getGuid());
        log.debug("New GUID:" + context.getGuid());
    }
}
