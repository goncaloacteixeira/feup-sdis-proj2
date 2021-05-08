package messages.chord;

import peer.chord.ChordReference;

import java.nio.charset.StandardCharsets;

public class Guid extends ChordMessage {
    private final int guid;

    public Guid(ChordReference sender, ChordReference originalSender, byte[] body) {
        super("CHORD", "GUID", sender, originalSender, body);
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
                ", originalSender=" + originalSender +
                ", guid=" + guid +
                '}';
    }
}
