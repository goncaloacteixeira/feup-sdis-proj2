package messages.chord;

import peer.chord.ChordReference;

public class Guid extends ChordMessage {
    private final int guid;

    public Guid(ChordReference sender, byte[] body) {
        super("CHORD", "GUID", sender, body);
        this.guid = Integer.parseInt(new String(body));
    }

    public int getGuid() {
        return guid;
    }

    @Override
    public String toString() {
        return "Guid{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", guid=" + guid +
                '}';
    }
}
