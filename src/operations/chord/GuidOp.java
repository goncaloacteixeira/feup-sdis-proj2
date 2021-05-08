package operations.chord;

import messages.Message;
import messages.chord.Guid;
import messages.chord.Lookup;
import messages.chord.LookupReply;
import peer.Peer;
import peer.chord.ChordReference;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class GuidOp extends ChordOperation {
    public GuidOp(SocketChannel channel, SSLEngine engine, Guid message, Peer context) {
        super(channel, engine, message, context);
    }

    @Override
    public void run() {
        log.debug("Started GUID operation...");

        context.getBootPeer().setGuid(message.getSender().getGuid());

        context.setGuid(((Guid) message).getGuid());
        log.debug("New GUID:" + context.getGuid());

        log.debug("Getting successor from peer: " + message.getSender().getGuid());

        Message message = new Lookup(context.getReference(), String.valueOf(context.getGuid()).getBytes(StandardCharsets.UTF_8));
        try {
            context.write(this.channel, this.engine, message.encode());
            LookupReply reply = (LookupReply) context.readWithReply(this.channel, this.engine);
            ChordReference successor = reply.getReference();
            log.debug("Got successor: " + successor);
            context.setSuccessor(successor);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
