package operations.chord;

import messages.Message;
import messages.chord.Lookup;
import messages.chord.LookupReply;
import peer.Peer;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.SocketChannel;
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

        if (context.successor() == null) {
            closest = self;
            sendReply(closest);
            return;
        } else if (ChordPeer.between(target, self.getGuid(), context.successor().getGuid(), false)) {
            closest = context.successor();
            sendReply(closest);
            return;
        } else {
            closest = context.closestPrecedingNode(target);
        }

        while (closest.getGuid() != target) {
            try {
                SSLConnection connection = context.connectToPeer(closest.getAddress(), true);
                if (connection.handshake()) {
                    Message message = new Lookup(self, String.valueOf(target).getBytes(StandardCharsets.UTF_8));
                    log.debug("Sending Lookup message to: " + closest.getGuid());
                    context.sendMessage(connection, message);
                    LookupReply reply = (LookupReply) context.readWithReply(connection);
                    context.closeConnection(connection);

                    if (reply.getReference() == null) continue;

                    if (closest.getGuid() == reply.getReference().getGuid() || reply.getReference().getGuid() == this.message.getSender().getGuid()) {
                        log.debug("Successor is same: " + closest);
                        sendReply(closest);
                        return;
                    }

                    closest = reply.getReference();

                    if (reply.getReference().getGuid() == context.getGuid()) {
                        log.debug("The successor is me!");
                        sendReply(closest);
                        return;
                    }

                    log.debug("New closest: " + closest.getGuid());
                } else {
                    log.debug("Handshake to peer failed");
                    return;
                }
            } catch (IOException e) {
                log.debug("Could not connect to peer.");
                return;
            } catch (Exception e) {
                log.error("Could not send message!: " + e + " localized: " + e.getLocalizedMessage());
                return;
            }
        }

        sendReply(closest);
    }

    private void sendReply(ChordReference reference) {
        log.debug("Sending closest peer: " + reference);

        Message message = new LookupReply(context.getReference(), reference.toString().getBytes(StandardCharsets.UTF_8));

        try {
            context.write(this.connection, message.encode());
        } catch (IOException e) {
            log.error("Error writing: " + e);
        }
    }
}
