package peer;

import messages.Message;
import messages.application.Ack;
import messages.application.Backup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;
import peer.ssl.MessageTimeoutException;
import peer.ssl.SSLConnection;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class Peer extends ChordPeer implements RemotePeer {
    private final static Logger log = LogManager.getLogger(Peer.class);
    private final String sap;
    private final ExecutorService executor = Executors.newFixedThreadPool(16);

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

    public String backup(String filename, int replicationDegree) {
        if (this.successor() == null || this.successor().getGuid() == this.guid) {
            return "Could not start BACKUP as this peer has not found other peers yet";
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
                return "Could not find Peers to Backup this file!";
            }

            log.info("Sending file to: {} with keys: {}", targetPeers, targetKeys);
            List<Future<String>> tasks = new ArrayList<>();

            for (ChordReference targetPeer : targetPeers) {
                String body = String.join("::", Arrays.asList(fileId, String.valueOf(size), this.getReference().toString(), targetKeys.get(targetPeers.indexOf(targetPeer)).toString()));
                Backup message = new Backup(this.getReference(), body.getBytes(StandardCharsets.UTF_8));

                Callable<String> runnable = () -> backup(targetPeer, file, message);
                tasks.add(executor.submit(runnable));
            }


            StringBuilder result = new StringBuilder("----------------------------------------------------------------");
            result.append(String.format("Result for %s with replication degree %d\n", filename, replicationDegree));
            for (Future<String> task : tasks) {
                String peerResult = task.get();
                result.append(peerResult).append("\n");
            }
            result.append("----------------------------------------------------------------");

            return result.toString();
        } catch (IOException e) {
            return "Failed to BACKUP file: " + e.getMessage();
        } catch (ExecutionException e) {
            return "Failed to BACKUP file on one Peer: " + e.getMessage();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "File Backed Up!";
    }

    private String backup(ChordReference target, File file, Backup message) {
        try {
            log.info("Starting backup for {} on Peer: {}", file.getName(), target);
            SSLConnection connection = this.connectToPeer(target.getAddress());
            this.send(connection, message);
            log.info("Waiting ACK from Peer: {}...", target);
            Message reply = this.receiveBlocking(connection, 100);
            if (!(reply instanceof Ack)) return "Failed to receive ACK from Peer after BACKUP request";

            FileChannel fileChannel = FileChannel.open(file.toPath());
            log.info("Sending file to Peer {}...", target);
            this.sendFile(connection, fileChannel);
            log.info("File sent to Peer {}!", target);

            log.info("Waiting ACK from Peer: {}...", target);
            reply = this.receiveBlocking(connection, 2000);
            if (!(reply instanceof Ack)) return "Failed to receive ACK from peer after sending file";
            log.info("Received ACK from Peer {}!", target);
            this.closeConnection(connection);
        } catch (IOException | MessageTimeoutException e) {
            return "Failed to Backup file on Peer " + target;
        }

        return "Backup Successful on Peer " + target;
    }

    public void start() {
        this.join();
        this.startPeriodicChecks();
    }

    @Override
    public String chord() {
        return "GUID: " + this.guid + "\n" +
                "Server Address: " + this.address + "\n" +
                "Predecessor: " + this.predecessor + "\n" +
                "Finger Table:" + "\n" +
                this.getRoutingTableString();
    }

    public String getServiceAccessPoint() {
        return sap;
    }

    public ChordReference getReference() {
        return new ChordReference(this.address, this.guid);
    }
}
