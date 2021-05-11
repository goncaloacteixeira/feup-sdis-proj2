package messages.chord;

import peer.chord.ChordReference;

public class LookupReply extends ChordMessage {
    private final ChordReference reference;
    private final boolean keepLooking;

    public LookupReply(ChordReference sender, byte[] body) {
        super("CHORD", "LOOKUPREPLY", sender, body);

        String[] parts = new String(body).split("::");

        this.reference = ChordReference.parse(parts[0]);
        this.keepLooking = Boolean.parseBoolean(parts[1]);
    }

    @Override
    public String toString() {
        return "LookupReply{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", reference=" + reference +
                ", keepLooking=" + keepLooking +
                '}';
    }

    public boolean keepLooking() {
        return keepLooking;
    }

    public ChordReference getReference() {
        return reference;
    }
}
