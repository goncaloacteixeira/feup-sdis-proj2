package operations.chord;

import messages.chord.Guid;
import peer.Peer;
import peer.ssl.SSLConnection;

public class GuidOp extends ChordOperation {
    public GuidOp(SSLConnection connection, Guid message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        log.debug("Started GUID operation...");

        context.getBootPeer().setGuid(message.getSender().getGuid());

        context.setGuid(((Guid) message).getGuid());
        log.debug("New GUID:" + context.getGuid());

        log.debug("Getting successor from peer: " + message.getSender().getGuid());

        context.setSuccessor(context.findSuccessor(message.getSender(), context.getGuid()));
    }
}
