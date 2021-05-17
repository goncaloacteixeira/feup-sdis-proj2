package peer;

import client.ClientCallbackInterface;
import messages.Message;
import messages.application.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.backend.PeerFile;
import peer.backend.PeerInternalState;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;
import peer.ssl.MessageTimeoutException;
import peer.ssl.SSLConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Executable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class Peer extends ChordPeer implements RemotePeer {
    private final static Logger log = LogManager.getLogger(Peer.class);
    private final String sap;
    private final ExecutorService executor = Executors.newFixedThreadPool(16);
    private final ExecutorService clientRequests = Executors.newFixedThreadPool(8);
    private ClientCallbackInterface callbackInterface;

    public static void main(String[] args) throws UnknownHostException {
        if (args.length < 3) {
            System.out.println("Usage: java Peer <Service Access Point> <BOOT PEER IP> <BOOT PEER PORT> [-b]");
            System.out.println("Service Access Point: RMI bind");
            return;
        }

        String sap = args[0];
        InetAddress bootAddress = InetAddress.getByName(args[1]);
        int bootPort = Integer.parseInt(args[2]);
        boolean boot = (args.length > 3 && args[3].equals("-b"));

        try {
            Peer peer = new Peer(new InetSocketAddress(bootAddress, bootPort), boot, sap);

            try {
                RemotePeer stub = (RemotePeer) UnicastRemoteObject.exportObject(peer, 0);
                Registry registry = LocateRegistry.getRegistry();
                registry.rebind(peer.getServiceAccessPoint(), stub);
                log.info("[RMI] Registry Complete");
                log.info("[RMI] Service Access Point: " + peer.getServiceAccessPoint());
            } catch (Exception e) {
                log.error("[RMI] Registry Exception: " + e.getCause());
                log.info("[RMI] Will continue but RMI is offline");
            }

            log.info("Peer Initiated");
            new Thread(peer::start).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Peer(InetSocketAddress address, boolean boot, String sap) throws Exception {
        super(address, boot);
        this.sap = sap;
    }

    public void register(ClientCallbackInterface callbackInterface) {
        this.callbackInterface = callbackInterface;
    }

    @Override
    public void backup(String filename, int replicationDegree) {
        clientRequests.submit(() -> _backup(filename, replicationDegree));
    }

    private void sendNotification(String message) {
        if (this.callbackInterface != null) {
            try {
                this.callbackInterface.notify(message);
            } catch (RemoteException e) {
                log.error("Caught an exception on RMI");
            }
        }
    }

    private void _backup(String filename, int replicationDegree) {
        if (!this.isActive()) {
            sendNotification("Peer's Server is not online yet!");
            return;
        }

        if (this.successor() == null || this.successor().getGuid() == this.guid) {
            sendNotification("Could not start BACKUP as this peer has not found other peers yet");
            return;
        }

        File file = new File(filename);
        BasicFileAttributes attributes;
        try {
            // Assemble the Payload for the BACKUP protocol message
            attributes = Files.getFileAttributeView(file.toPath(), BasicFileAttributeView.class).readAttributes();
            long size = attributes.size();
            String fileId = Utils.generateHashForFile(filename, attributes);


            List<Integer> keys = new ArrayList<>();
            ThreadLocalRandom.current().ints(0, Constants.CHORD_MAX_PEERS).distinct().limit(replicationDegree * 3L).forEach(keys::add);

            log.info("Keys: {}", keys);

            List<ChordReference> targetPeers = new ArrayList<>();
            List<Integer> targetKeys = new ArrayList<>();

            for (int key : keys) {
                ChordReference peer = findSuccessor(key);
                if (peer.getGuid() != this.guid && !targetPeers.contains(peer)) {
                    targetPeers.add(peer);
                    targetKeys.add(key);
                }
                if (targetPeers.size() == replicationDegree) break;
            }

            if (targetPeers.size() == 0) {
                sendNotification("Could not find Peers to Backup this file!");
                return;
            }

            PeerFile peerFile;
            if (this.internalState.getSentFilesMap().containsKey(filename)) {
                peerFile = this.internalState.getSentFilesMap().get(filename);
            } else {
                peerFile = new PeerFile(-1, fileId, this.getReference(), size, replicationDegree);
            }

            log.info("Sending file to: {} with keys: {}", targetPeers, targetKeys);
            List<Future<String>> tasks = new ArrayList<>();

            for (ChordReference targetPeer : targetPeers) {
                String body = String.join("::",
                        Arrays.asList(fileId,
                                String.valueOf(size),
                                this.getReference().toString(),
                                targetKeys.get(targetPeers.indexOf(targetPeer)).toString(),
                                String.valueOf(replicationDegree)
                        ));
                Backup message = new Backup(this.getReference(), body.getBytes(StandardCharsets.UTF_8));

                Callable<String> runnable = () -> backup(targetPeer, file, message, peerFile);
                tasks.add(executor.submit(runnable));
            }


            StringBuilder result = new StringBuilder("----------------------------------------------------------------\n");
            result.append(String.format("Result for %s with replication degree %d\n", filename, replicationDegree));
            for (Future<String> task : tasks) {
                String peerResult = task.get();
                result.append(peerResult).append("\n");
            }
            result.append("----------------------------------------------------------------");

            this.internalState.addSentFile(filename, peerFile);

            sendNotification(result.toString());
        } catch (IOException e) {
            sendNotification("Failed to BACKUP file: " + e.getMessage());
        } catch (ExecutionException e) {
            sendNotification("Failed to BACKUP file on one Peer: " + e.getMessage());
        } catch (InterruptedException e) {
            sendNotification("Interrupted Exception");
        }
    }

    private String backup(ChordReference target, File file, Backup message, PeerFile peerFile) {
        try {
            log.info("Starting backup for {} on Peer: {}", file.getName(), target);
            SSLConnection connection = this.connectToPeer(target.getAddress());
            this.send(connection, message);
            log.info("Waiting ACK from Peer: {}...", target);
            Message reply = this.receiveBlocking(connection, 100);

            if (reply instanceof Ack) {
                // continue
            } else if (reply instanceof Nack) {
                this.closeConnection(connection);
                if (((Nack) reply).getMessage().equals("NOSPACE")) {
                    return String.format("Peer %s has no space to store the file", target);
                } else if (((Nack) reply).getMessage().equals("HAVEFILE")) {
                    peerFile.addKey(message.getKey());
                    return String.format("Peer %s already has the file", target);
                } else {
                    return String.format("Received unexpected message from Peer: %s", target);
                }
            } else {
                return String.format("Received unexpected message from Peer: %s", target);
            }

            FileChannel fileChannel = FileChannel.open(file.toPath());
            log.info("Sending file to Peer {}...", target);
            this.sendFile(connection, fileChannel);
            log.info("File sent to Peer {}!", target);

            log.info("Waiting ACK from Peer: {}...", target);
            reply = this.receiveBlocking(connection, 2000);
            if (!(reply instanceof Ack)) return "Failed to receive ACK from peer after sending file";
            log.info("Received ACK from Peer {}!", target);
            this.closeConnection(connection);

            peerFile.addKey(message.getKey());
        } catch (IOException | MessageTimeoutException e) {
            e.printStackTrace();
            return "Failed to Backup file on Peer " + target;
        }

        return "Backup Successful on Peer " + target;
    }

    public boolean receiveFile(SSLConnection connection, PeerFile peerFile, String filename) {
        log.info("Starting GET...");

        String fileId = peerFile.getId();
        ChordReference owner = peerFile.getOwner();
        long size = peerFile.getSize();
        int key = peerFile.getKey();
        int replicationDegree = peerFile.getReplicationDegree();

        // send GET message for fileID
        this.send(connection, new Get(this.getReference(), peerFile.getId().getBytes(StandardCharsets.UTF_8)));
        log.info("GET sent!");

        // receive Acknowledgement
        Message ack;
        try {
            ack = this.receiveBlocking(connection, 500);
        } catch (MessageTimeoutException e) {
            log.error("Could not receive ACK for GET message on for {}", peerFile);
            return false;
        }
        if (ack instanceof Ack) {
            // proceed
        } else if (ack instanceof Nack) {
            log.error("Not found: {}", peerFile);
            return false;
        }

        // send new GET message, remote peer will start to write file to socket
        this.send(connection, new Get(this.getReference(), peerFile.getId().getBytes(StandardCharsets.UTF_8)));

        log.info("Getting file: {}", peerFile);

        try {
            FileOutputStream outputStream = new FileOutputStream(this.getFileLocation(filename));
            FileChannel fileChannel = outputStream.getChannel();
            log.info("Ready to receive file...");
            connection.setPeerNetData(ByteBuffer.allocate(Constants.TLS_CHUNK_SIZE));
            connection.getSocketChannel().configureBlocking(true);
            this.receiveFile(connection, fileChannel, size);
            fileChannel.close();
            log.info("Received file!");

            this.closeConnection(connection);

            this.addSavedFile(key, fileId, owner, size, replicationDegree);
            return true;
        } catch (IOException e) {
            log.error("Error receiving file: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void restore(String filename) throws RemoteException {
        clientRequests.submit(() -> _restore(filename));
    }

    private void _restore(String filename) {
        log.info("Starting RESTORE protocol for: {}", filename);

        PeerFile peerFile = this.internalState.getSentFilesMap().get(filename);
        if (peerFile == null) {
            sendNotification("File was not backed up: " + filename);
            return;
        }

        String newFilename = "restored_" + new File(filename).getName();

        // send get for each peer, if a nack is received abort and go to the next one
        for (Integer key : peerFile.getKeys()) {
            ChordReference reference = this.findSuccessor(key);
            SSLConnection connection = this.connectToPeer(reference.getAddress());
            if (connection == null) continue;

            boolean result = this.receiveFile(connection, peerFile, newFilename);
            if (result) {
                log.info("Restored file: {} under: {}", filename, newFilename);
                sendNotification("File: " + filename + " restored successfully!");
                return;
            }
        }

        sendNotification("File: " + filename + " could not be restored!");
    }

    @Override
    public void delete(String filename) throws RemoteException {
        clientRequests.submit(() -> _delete(filename));
    }

    private void _delete(String filename) {
        log.info("Starting DELETE client request for {}", filename);

        PeerFile file = this.internalState.getSentFilesMap().get(filename);

        if (file == null) {
            log.error("File {} is not backed up", filename);
            sendNotification(String.format("File %s was not backed up!", filename));
            return;
        }

        Set<ChordReference> targetPeers = new HashSet<>();

        for (Integer key : file.getKeys()) {
            ChordReference reference = this.findSuccessor(key);
            if (reference == null) continue;
            targetPeers.add(reference);
            Runnable executable = () -> sendDelete(file, reference);
            executor.submit(executable);
        }

        log.info("All DELETE requests processed!");
        sendNotification(String.format("DELETE for %s was sent to:\n%s", filename, targetPeers));
    }

    private void sendDelete(PeerFile file, ChordReference reference) {
        SSLConnection connection = this.connectToPeer(reference.getAddress());
        if (connection == null) return;
        this.send(connection, new Delete(this.getReference(), file.getId().getBytes(StandardCharsets.UTF_8)));
        log.info("Sent DELETE to {} for {}", reference, file.getId());
    }

    @Override
    public void state() throws RemoteException {
        log.info("Received STATE client request");
        clientRequests.submit(this::_state);
    }

    private String _state() {
        String state = this.internalState.toString();
        if (callbackInterface != null) {
            try {
                callbackInterface.notify(state);
            } catch (RemoteException e) {
                log.error("Could not notify client");
            }
        }
        return state;
    }

    @Override
    public void clientFindSuccessor(int guid) {
        clientRequests.submit(() -> _clientFindSuccessor(guid));
    }

    private void _clientFindSuccessor(int guid) {
        sendNotification(super.findSuccessor(guid).toString());
    }

    public String getFileLocation(String fileId) {
        return String.format(PeerInternalState.FILES_PATH, this.guid, fileId);
    }

    public void addSavedFile(int key, String id, ChordReference owner, long size, int replicationDegree) {
        PeerFile file = new PeerFile(key, id, owner, size, replicationDegree);
        this.internalState.addSavedFile(file);
    }

    public List<PeerFile> getSavedFiles() {
        return new ArrayList<>(this.internalState.getSavedFilesMap().values());
    }

    public ConcurrentHashMap<String, PeerFile> getSentFilesMap() {
        return this.internalState.getSentFilesMap();
    }

    public ConcurrentHashMap<String, PeerFile> getSavedFilesMap() {
        return this.internalState.getSavedFilesMap();
    }

    public PeerFile getSavedFile(String fileId) {
        return this.internalState.getSavedFilesMap().get(fileId);
    }

    public void start() {
        this.join();
    }

    @Override
    public void chord() {
        sendNotification("GUID: " + this.guid + "\n" +
                "Server Address: " + this.address + "\n" +
                "Predecessor: " + this.predecessor + "\n" +
                "Finger Table:" + "\n" +
                this.getRoutingTableString());
    }

    public String getServiceAccessPoint() {
        return sap;
    }

    public ChordReference getReference() {
        return new ChordReference(this.address, this.guid);
    }
}
