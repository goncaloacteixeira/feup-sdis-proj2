package operations.chord;

import messages.Message;
import messages.chord.Lookup;
import messages.chord.LookupReply;
import peer.Peer;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LookupOp extends ChordOperation {
    public LookupOp(SSLConnection connection, Lookup message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        int target = ((Lookup) this.message).getTarget();

        log.debug("Started Lookup for:" + target);
        ChordReference self = context.getReference();
        ChordReference closest;
        boolean keepLooking = false;

        if (context.successor() == null) {
            closest = self;
        } else if (ChordPeer.between(target, self.getGuid(), context.successor().getGuid(), false)) {
            closest = context.successor();
        } else {
            keepLooking = true;
            closest = context.closestPrecedingNode(target);
        }

        log.debug("Sending closest peer: " + closest);

        String body = String.format("%s::%s", closest, keepLooking ? "true" : "false");

        Message message = new LookupReply(context.getReference(), body.getBytes(StandardCharsets.UTF_8));

        try {
            context.send(this.connection, message);
        } catch (IOException e) {
            log.error("Error writing: " + e);
        }
    }
}
