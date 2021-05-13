package messages.chord;

import peer.chord.ChordReference;

public class Lookup extends ChordMessage {
    private final int target;

    public Lookup(ChordReference sender, byte[] body) {
        super("CHORD", "LOOKUP", sender, body);

        this.target = Integer.parseInt(new String(body));
    }

    @Override
    public String toString() {
        return "Lookup{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", target=" + target +
                '}';
    }

    public int getTarget() {
        return target;
    }
}
