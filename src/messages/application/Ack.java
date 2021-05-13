package messages.application;

import operations.Operation;
import peer.Peer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import java.nio.charset.StandardCharsets;

public class Ack extends ApplicationMessage {
    public Ack(ChordReference sender) {
        super("ACK", sender);
    }

    @Override
    public byte[] encode() {
        return String.format("%s %s \r\n %s \r\n\r\n",
                "APP", this.sender,
                this.operation).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Operation getOperation(Peer context, SSLConnection connection) {
        // Should be expecting on backup/restore operation, no need to delegate the task
        return null;
    }
}
