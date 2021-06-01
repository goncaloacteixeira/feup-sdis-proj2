package operations.application;

import messages.application.ApplicationMessage;
import messages.application.Backup;
import messages.application.Removed;
import peer.Constants;
import peer.Peer;
import peer.backend.PeerFile;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

public class RemovedOp extends AppOperation {
    public RemovedOp(SSLConnection connection, ApplicationMessage message, Peer context) {
        super(connection, message, context);
    }

    @Override
    public void run() {
        this.context.closeConnection(connection);

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

        if (file.getValue().beingDeleted) {
            if (file.getValue().getKeys().isEmpty()) {
                log.info("File {} is no longer being backed by any peer!", file);
                context.getSentFilesMap().remove(file.getKey());
            }
        } else {
            log.info("Replication degree dropped bellow the desired, starting new backup...");

            List<Integer> keys = new ArrayList<>();
            ThreadLocalRandom.current().ints(0, Constants.CHORD_MAX_PEERS).distinct().limit(file.getValue().getReplicationDegree() * 2L).forEach(keys::add);
            ChordReference targetPeer = null;
            Integer targetKey = null;

            List<ChordReference> currentPeers = new ArrayList<>();
            for (int k : file.getValue().getKeys()) {
                currentPeers.add(context.findSuccessor(k));
            }

            for (int k : keys) {
                ChordReference peer = context.findSuccessor(k);
                if (peer.getGuid() != this.context.getGuid() && !currentPeers.contains(peer)) {
                    targetPeer = peer;
                    targetKey = k;
                    break;
                }
            }

            if (targetPeer != null) {
                String body = String.join("::",
                        Arrays.asList(fileId,
                                String.valueOf(file.getValue().getSize()),
                                file.getValue().getOwner().toString(),
                                targetKey.toString(),
                                String.valueOf(file.getValue().getReplicationDegree())
                        ));
                Backup message = new Backup(context.getReference(), body.getBytes(StandardCharsets.UTF_8));

                File localFile = new File(context.getFileLocation(fileId));

                ChordReference finalTargetPeer = targetPeer;
                Map.Entry<String, PeerFile> finalFile = file;
                Callable<String> runnable = () -> context.backup(finalTargetPeer, localFile, message, finalFile.getValue());

                context.PROTOCOL_EXECUTOR.submit(runnable);

                log.info("BACKUP submitted for {}", file.getValue());
            }
        }


    }
}
