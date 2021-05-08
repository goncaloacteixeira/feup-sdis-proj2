package messages.chord;

import peer.chord.ChordReference;

import java.nio.charset.StandardCharsets;

public class Join extends ChordMessage {
    public Join(ChordReference sender, ChordReference originalSender) {
        super("CHORD", "JOIN", sender, originalSender);
    }

    @Override
    public byte[] encode() {
        return String.format("%s %s %s \r\n %s \r\n\r\n",
                "CHORD", this.sender, this.originalSender,
                this.operation).getBytes(StandardCharsets.UTF_8);
    }
}
