package operations.chord;

import messages.chord.ChordMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;

public class JoinOp extends ChordOperation {
    private static final Logger log = LogManager.getLogger(JoinOp.class);

    public JoinOp(ChordMessage message, Peer context) {
        super(message, context);
    }

    @Override
    public void run() {
        log.debug(String.format("Started JOIN operation:\n message: %s\n context: %s", message, context));
    }
}
