package operations.application;

import messages.application.Ack;
import messages.application.ApplicationMessage;
import messages.application.Backup;
import messages.application.Nack;
import operations.chord.ChordOperation;
import peer.Constants;
import peer.Peer;
import peer.Utils;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class BackupOp extends AppOperation {
    public BackupOp(SSLConnection connection, ApplicationMessage message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        String fileId = ((Backup) message).getFileID();
        ChordReference owner = ((Backup) message).getOwner();
        long size = ((Backup) message).getSize();
        int key = ((Backup) message).getKey();
        int replicationDegree = ((Backup) message).getReplicationDegree();

        // test capacity
        if (!context.hasSpace(size)) {
            log.info("No space to store file with size: {}", Utils.prettySize(size));
            context.send(this.connection, new Nack(this.context.getReference(), "NOSPACE".getBytes(StandardCharsets.UTF_8)));
            return;
        } else if (this.context.getSavedFile(fileId) != null) {
            log.info("Already have this file backed up!");
            context.send(this.connection, new Nack(this.context.getReference(), "HAVEFILE".getBytes(StandardCharsets.UTF_8)));
            return;
        }

        log.info("Staring backup on fileId: {} for owner: {} with size: {}", fileId, owner, Utils.prettySize(size));

        try {
            FileOutputStream outputStream = new FileOutputStream(this.context.getFileLocation(fileId));
            FileChannel fileChannel = outputStream.getChannel();
            log.info("Ready to receive file...");
            context.send(this.connection, new Ack(this.context.getReference()));
            connection.setPeerNetData(ByteBuffer.allocate(Constants.TLS_CHUNK_SIZE));
            connection.getSocketChannel().configureBlocking(true);
            context.receiveFile(connection, fileChannel, ((Backup) this.message).getSize());
            fileChannel.close();
            log.info("Received file!");
            log.info("Sending ACK to client so they can close connection");
            context.send(this.connection, new Ack(this.context.getReference()));

            this.context.addSavedFile(key, fileId, owner, size, replicationDegree);
        } catch (IOException e) {
            log.error("Error receiving file: {}", e.getMessage());
        }
    }
}
