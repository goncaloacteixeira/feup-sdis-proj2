package operations.application;

import messages.application.ApplicationMessage;
import messages.application.Delete;
import messages.application.Removed;
import peer.Peer;
import peer.backend.PeerFile;
import peer.ssl.SSLConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class DeleteOp extends AppOperation {
    public DeleteOp(SSLConnection connection, ApplicationMessage message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        String fileId = ((Delete) message).getFileId();

        log.info("Received DELETE request for: {}", fileId);

        PeerFile file = context.getSavedFile(fileId);
        if (file == null) {
            this.context.closeConnection(connection);
            return;
        }

        this.context.closeConnection(connection);

        try {
            if (Files.deleteIfExists(Path.of(context.getFileLocation(fileId)))) {
                String body = String.join(":", Arrays.asList(fileId, String.valueOf(file.getKey())));

                SSLConnection connection = this.context.connectToPeer(message.getSender().getAddress());
                assert connection != null;

                log.info("Removed file: {}", file);
                this.context.getSavedFilesMap().remove(fileId);
                this.context.send(connection, new Removed(context.getReference(), body.getBytes(StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            log.error("Error deleting file: {}: {}", file, e.getMessage());
        }
    }
}
