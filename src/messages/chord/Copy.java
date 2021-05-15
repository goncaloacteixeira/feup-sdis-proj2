package messages.chord;

import peer.chord.ChordReference;

public class Copy extends ChordMessage {
    public Copy(ChordReference sender) {
        super("CHORD", "COPY", sender);
    }
}
