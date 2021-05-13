package operations.application;

import messages.application.Ack;
import messages.application.ApplicationMessage;
import messages.application.Backup;
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
            e.printStackTrace();
        }
    }
}
