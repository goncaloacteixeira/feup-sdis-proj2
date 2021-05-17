package messages.application;

import peer.chord.ChordReference;

public class Removed extends ApplicationMessage {
    private final String fileId;
    private final int key;

    public Removed(ChordReference sender, byte[] body) {
        super("REMOVED", sender, body);

        String[] parts = new String(body).split(":");

        fileId = parts[0];
        key = Integer.parseInt(parts[1]);
    }

    public String getFileId() {
        return fileId;
    }

    public int getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "Removed{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", fileId='" + fileId + '\'' +
                ", key=" + key +
                '}';
    }
}
