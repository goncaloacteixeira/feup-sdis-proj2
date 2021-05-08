package messages.chord;

import messages.Message;
import operations.Operation;
import operations.chord.ChordOperation;
import peer.Peer;
import peer.chord.ChordReference;

import java.nio.charset.StandardCharsets;

public abstract class ChordMessage extends Message {

    public ChordMessage(String type, String operation, ChordReference sender, ChordReference originalSender) {
        super(type, operation, sender, originalSender);
    }

    public ChordMessage(String type, String operation, ChordReference sender, ChordReference originalSender, byte[] body) {
        super(type, operation, sender, originalSender, body);
    }

    public static ChordMessage parse(ChordReference sender, ChordReference original, String header) {
        header = header.replaceAll("^ +| +$|( )+", "$1").trim();
        String[] args = header.split(" ");
        String chordType = args[0];

        switch (chordType) {
            case "JOIN":
                return new Join(sender, original);
            default:
                return null;
        }
    }

    @Override
    public Operation getOperation(Peer context) {
        return ChordOperation.parse(this, context);
    }

    @Override
    public byte[] encode() {
        byte[] header = String.format("%s %s %s \r\n %s \r\n\r\n",
                "CHORD", this.sender, this.originalSender,
                this.operation).getBytes(StandardCharsets.UTF_8);

        byte[] toSend = new byte[header.length + this.body.length];
        System.arraycopy(header, 0, toSend, 0, header.length);
        System.arraycopy(this.body, 0, toSend, header.length, body.length);
        return toSend;
    }
}
