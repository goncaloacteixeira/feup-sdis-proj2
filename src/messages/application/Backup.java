package messages.application;

import operations.Operation;
import operations.application.BackupOp;
import peer.Peer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

public class Backup extends ApplicationMessage {
    private final String fileID;
    private final long size;
    private final ChordReference owner;
    private final int key;
    private final int replicationDegree;

    public Backup(ChordReference sender, byte[] body) {
        super("BACKUP", sender, body);

        /* <fileId>::<size>::<owner> */
        /* owner: chord reference */

        String[] parts = new String(body).split("::");

        fileID = parts[0];
        size = Long.parseLong(parts[1]);
        owner = ChordReference.parse(parts[2]);
        key = Integer.parseInt(parts[3]);
        replicationDegree = Integer.parseInt(parts[4]);
    }

    @Override
    public byte[] encode() {
        return super.encode();
    }

    public String getFileID() {
        return fileID;
    }

    public long getSize() {
        return size;
    }

    public int getKey() {
        return key;
    }

    public ChordReference getOwner() {
        return owner;
    }

    public int getReplicationDegree() {
        return replicationDegree;
    }

    @Override
    public Operation getOperation(Peer context, SSLConnection connection) {
        return new BackupOp(connection, this, context);
    }
}
