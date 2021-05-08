package messages.chord;

import peer.chord.ChordReference;

public class Notification extends ChordMessage {
    private final ChordReference reference;

    public Notification(ChordReference sender, byte[] body) {
        super("CHORD", "NOTIFICATION", sender, body);

        this.reference = ChordReference.parse(new String(body));
    }

    @Override
    public String toString() {
        return "Notification{" +
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
