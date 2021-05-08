package messages.chord;

import peer.chord.ChordReference;

import java.nio.charset.StandardCharsets;

public class Predecessor extends ChordMessage {
    public Predecessor(ChordReference sender) {
        super("CHORD", "PREDECESSOR", sender);
    }

    @Override
    public byte[] encode() {
        return String.format("%s %s \r\n %s \r\n\r\n",
                "CHORD", this.sender,
                this.operation).getBytes(StandardCharsets.UTF_8);
    }
}
