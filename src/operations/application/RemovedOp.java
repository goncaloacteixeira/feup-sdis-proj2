package operations.application;

import messages.application.ApplicationMessage;
import messages.application.Removed;
import peer.Peer;
import peer.backend.PeerFile;
import peer.ssl.SSLConnection;

import java.util.Map;

public class RemovedOp extends AppOperation {
    public RemovedOp(SSLConnection connection, ApplicationMessage message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        String fileId = ((Removed) message).getFileId();
        int key = ((Removed) message).getKey();

        Map.Entry<String, PeerFile> file = null;
        for (Map.Entry<String, PeerFile> entry : this.context.getSentFilesMap().entrySet()) {
            if (entry.getValue().getId().equals(fileId)) {
                file = entry;
                break;
            }
        }

        if (file == null) return;

        file.getValue().getKeys().remove(key);

        if (file.getValue().getKeys().isEmpty()) {
            log.info("File {} is no longer being backed by any peer!", file);
            context.getSentFilesMap().remove(file.getKey());
        }

        this.context.closeConnection(connection);
    }
}
