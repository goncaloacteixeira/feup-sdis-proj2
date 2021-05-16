package messages.application;

import peer.chord.ChordReference;

public class Get extends ApplicationMessage {
    private final String fileId;

    public Get(ChordReference sender, byte[] body) {
        super("GET", sender, body);

        fileId = new String(body);
    }

    public String getFileId() {
        return fileId;
    }
}

