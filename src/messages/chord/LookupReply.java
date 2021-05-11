package messages.chord;

import peer.chord.ChordReference;

public class LookupReply extends ChordMessage {
    private final ChordReference reference;

    public LookupReply(ChordReference sender, byte[] body) {
        super("CHORD", "LOOKUPREPLY", sender, body);

        this.reference = ChordReference.parse(new String(body));
    }

    @Override
    public String toString() {
        return "LookupReply{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", reference=" + reference +
                '}';
    }

    public ChordReference getReference() {
        return reference;
    }
}
