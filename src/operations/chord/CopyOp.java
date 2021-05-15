package operations.chord;

import messages.Message;
import messages.chord.ChordMessage;
import messages.chord.CopyReply;
import peer.Peer;
import peer.backend.PeerFile;
import peer.ssl.SSLConnection;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CopyOp extends ChordOperation {
    public CopyOp(SSLConnection connection, ChordMessage message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        int key = message.getSender().getGuid();

        List<String> keys = new ArrayList<>();

        for (PeerFile file : context.getSavedFiles()) {
            if (file.getKey() < message.getSender().getGuid())
                keys.add(String.format("%d|%s|%s|%d|%d", file.getKey(), file.getId(), file.getOwner(), file.getSize(), file.getReplicationDegree()));
        }

        Message reply;
        if (keys.isEmpty()) {
            reply = new CopyReply(this.context.getReference(), "NONE".getBytes(StandardCharsets.UTF_8));
        } else {
            byte[] replyBody = String.join("::", keys).getBytes(StandardCharsets.UTF_8);

            reply = new CopyReply(this.context.getReference(), replyBody);
        }

        context.send(connection, reply);
    }
}
