package messages.application;

import peer.chord.ChordReference;

public class Delete extends ApplicationMessage {
    private final String fileId;

    public Delete(ChordReference sender, byte[] body) {
        super("DELETE", sender, body);

        this.fileId = new String(body);
    }

    public String getFileId() {
        return fileId;
    }

    @Override
    public String toString() {
        return "Delete{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", fileId='" + fileId + '\'' +
                '}';
    }
}
