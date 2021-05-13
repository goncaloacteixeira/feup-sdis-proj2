package messages.chord;

import peer.chord.ChordReference;

import java.nio.charset.StandardCharsets;

public class Join extends ChordMessage {
    public Join(ChordReference sender) {
        super("CHORD", "JOIN", sender);
    }

    @Override
    public byte[] encode() {
        return String.format("%s %s \r\n %s \r\n\r\n",
                "CHORD", this.sender,
                this.operation).getBytes(StandardCharsets.UTF_8);
    }
}
