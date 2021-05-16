package operations.application;

import messages.application.Ack;
import messages.application.ApplicationMessage;
import messages.application.Get;
import messages.application.Nack;
import peer.Peer;
import peer.backend.PeerFile;
import peer.ssl.MessageTimeoutException;
import peer.ssl.SSLConnection;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class GetOp extends AppOperation {
    public GetOp(SSLConnection connection, ApplicationMessage message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        // received get, check for fileID and then send ACK/NACK
        String fileID = ((Get) message).getFileId();

        log.info("Started GET Operation for: {}", fileID);

        PeerFile peerFile = context.getSavedFile(fileID);
        if (peerFile == null) {
            context.send(connection, new Nack(context.getReference(), "NOTFOUND".getBytes(StandardCharsets.UTF_8)));
            return;
        }
        log.info("Sending ACK and wait for GET...");
        // send ACK and wait for a new GET
        context.send(connection, new Ack(context.getReference()));

        Get message;
        try {
            message = (Get) context.receiveBlocking(connection, 500);
        } catch (MessageTimeoutException e) {
            log.error("Could not receive second GET message for: {}", fileID);
            return;
        }

        // safe call
        assert message != null;

        File file = new File(context.getFileLocation(fileID));
        FileChannel fileChannel;
        try {
            fileChannel = FileChannel.open(file.toPath());
        } catch (IOException e) {
            log.error("Could not access file {}: {}", fileID, e.getMessage());
            return;
        }

        log.info("Sending file...");
        this.context.sendFile(connection, fileChannel);
        log.info("File sent!");

        // client needs to close connection on their side
    }
}
