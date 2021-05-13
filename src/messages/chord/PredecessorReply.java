package messages.chord;

import peer.chord.ChordReference;

public class PredecessorReply extends ChordMessage {
    private final ChordReference predecessor;

    public PredecessorReply(ChordReference sender, byte[] body) {
        super("CHORD", "PREDECESSORREPLY", sender, body);

        String predecessor = new String(body);
        if (!predecessor.equals("nil")) {
            this.predecessor = ChordReference.parse(predecessor);
        } else {
            this.predecessor = null;
        }
    }

    public ChordReference getPredecessor() {
        return predecessor;
    }
}
