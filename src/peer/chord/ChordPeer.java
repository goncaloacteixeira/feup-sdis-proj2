package peer.chord;

import messages.Message;
import messages.chord.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Constants;
import peer.Peer;
import peer.backend.PeerFile;
import peer.backend.PeerInternalState;
import peer.ssl.MessageTimeoutException;
import peer.ssl.SSLConnection;
import peer.ssl.SSLPeer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Chord Peer extends an SSLPeer, the last provides a way to make connections to other peers, and
 * safely transmit messages between them. This Chord Peer could be a layer above the SSL Peer, in this
 * layer we do not want to be concerned with how the transport works.
 */
public abstract class ChordPeer extends SSLPeer {
    private final static Logger log = LogManager.getLogger(ChordPeer.class);

    protected int guid = -1;
    protected boolean boot;
    protected ChordReference bootPeer;
    protected ChordReference predecessor;
    protected ChordReference[] routingTable = new ChordReference[Constants.M_BIT];
    protected ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(2);
    protected ExecutorService executorService = Executors.newFixedThreadPool(16);
    private int nextFinger = 1;

    /**
     * Constructor for the Chord Peer, it takes an Address and a (debug) Flag to signal if this peer
     * should start the Chord Circle or not. The address is passed on to the SSL layer to start the server.
     *
     * @param address Address used to listen to client connections
     * @param boot    (debug) flag used to signal if this peer should create the Chord Circle or not
     * @throws Exception On error starting the SSLPeer
     */
    public ChordPeer(InetSocketAddress address, boolean boot) throws Exception {
        super(address, boot);
        this.boot = boot;
        this.bootPeer = new ChordReference(address, -1);
    }

    /**
     * Method to start the async periodic checks, this check include the fixFingers, stabilize and checkPredecessor
     * methods. This was implemented based on this paper on Chord: https://pdos.csail.mit.edu/papers/chord:sigcomm01/chord_sigcomm.pdf
     */
    protected void startPeriodicChecks() {
        scheduler.scheduleAtFixedRate(this::fixFingers, 1, 3, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::stabilize, 3, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkPredecessor, 5, 15, TimeUnit.SECONDS);
    }

    /**
     * @return the immediate successor for this peer
     */
    public synchronized ChordReference successor() {
        return routingTable[0];
    }

    /**
     * @param position position to get a finger [1, M]
     * @return the N-th finger [1, M]
     */
    public synchronized ChordReference getFinger(int position) {
        return routingTable[position - 1];
    }

    /**
     * Method to set the n-th finger [1, M]
     *
     * @param position position to set the finger [1, M]
     * @param finger   finger to be set
     */
    public synchronized void setFinger(int position, ChordReference finger) {
        this.routingTable[position - 1] = finger;
    }

    public void setGuid(int guid) {
        this.guid = guid;
    }

    public int getGuid() {
        return guid;
    }

    public ChordReference getBootPeer() {
        return bootPeer;
    }

    /**
     * <h1>Join Method</h1>
     * This method attempts to insert this peer on the Chord Network, if this peer is not declared as boot.
     * It sends a JOIN message to the boot peer (passed as argument on the startup) and waits for a GUID message,
     * then it will ask the boot peer for it's successor.
     *
     * @return true if the join was successful
     */
    public boolean join() {
        if (this.boot) {
            this.guid = generateNewKey(this.address);
            this.setSuccessor(new ChordReference(this.address, this.guid));
            log.debug("Peer was started as boot, assigning GUID:" + this.guid);
            this.startPeriodicChecks();
            this.internalState = PeerInternalState.load((Peer) this);
            return true;
        }
        log.debug("Trying to join the CHORD circle on: " + this.bootPeer);

        SSLConnection bootPeerConnection = this.connectToPeer(bootPeer.getAddress());
        if (bootPeerConnection == null) {
            log.error("Aborting join operation...");
            return false;
        }

        ChordReference self = new ChordReference(this.address, this.guid);

        Message message = new Join(self);

        // send join
        this.send(bootPeerConnection, message);

        // wait for GUID message and also Successor
        // GUID task will automatically assign the GUID to the peer
        Message reply;
        try {
            reply = this.receiveBlocking(bootPeerConnection, 50);
        } catch (MessageTimeoutException e) {
            log.error("Could not receive message, aborting");
            // try to close connection
            this.closeConnection(bootPeerConnection);
            return false;
        }

        this.setSuccessor(bootPeer);
        reply.getOperation((Peer) this, bootPeerConnection).run();

        // Load or Create Internal State
        this.internalState = PeerInternalState.load((Peer) this);

        this.closeConnection(bootPeerConnection);
        this.setSuccessor(this.findSuccessor(bootPeer, this.guid));

        SSLConnection connection = this.connectToPeer(successor().getAddress());
        this.send(connection, new Copy(new ChordReference(this.address, this.guid)));
        CopyReply copyReply;
        try {
            copyReply = (CopyReply) this.receiveBlocking(connection, 100);
        } catch (MessageTimeoutException e) {
            log.error("Could not receive copy reply...");
            this.startPeriodicChecks();
            return true;
        }
        this.closeConnection(connection);

        List<PeerFile> files = copyReply.getFiles();
        if (files.size() == 0) {
            this.startPeriodicChecks();
            return true;
        }

        log.info("Reclaiming {} files...", files.size());
        List<Future<PeerFile>> ops = new ArrayList<>();
        for (PeerFile file : files) {
            Callable<PeerFile> op = () -> this.reclaimFile(file);
            ops.add(executorService.submit(op));
        }

        for (Future<PeerFile> op : ops) {
            try {
                log.info("Reclaimed: {}", op.get());
            } catch (InterruptedException | ExecutionException e) {
                log.error("Error processing operation");
            }
        }

        log.info("Files reclaimed!");

        this.startPeriodicChecks();
        return true;
    }

    /**
     * Method to reclaim a file which should be on this peer
     *
     * @param file File to be reclaimed
     * @return the reclaimed peer file
     * @see PeerFile
     */
    private PeerFile reclaimFile(PeerFile file) {
        SSLConnection newConnection = this.connectToPeer(successor().getAddress());
        boolean result = ((Peer) this).receiveFile(newConnection, file, file.getId());
        // connection is closed on receive file because there's nothing left to do on close

        if (result) {
            // TODO send delete for file ID
        } else {
            return null;
        }

        return file;
    }

    /**
     * Method to generate an hashed key based on a socket address. The hash used is SHA-1, and then it is converted to
     * its hash code (integer)
     *
     * @param address Address to be hashed
     * @return the hash's hash code
     */
    public static int generateNewKey(InetSocketAddress address) {
        String toHash = address.getAddress().getHostAddress() + ":" + address.getPort();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return -1;
        }
        byte[] hashed = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
        return new String(hashed).hashCode() & 0x7fffffff % Constants.CHORD_MAX_PEERS;
    }

    /**
     * Method used to promote this peer to boot peer.
     */
    @Deprecated
    private void promote() {
        this.guid = generateNewKey(this.address);
        this.bootPeer = new ChordReference(this.address, this.guid);
        this.boot = true;
    }

    public synchronized ChordReference[] getRoutingTable() {
        return routingTable;
    }

    public synchronized String getRoutingTableString() {
        return routingTableToString(this.routingTable);
    }

    /**
     * Method to set this peer's successor
     *
     * @param finger Finger to be set as successor
     */
    public synchronized void setSuccessor(ChordReference finger) {
        this.setFinger(1, finger);
    }

    /**
     * Method to return the routing table as a string, used to display the finger table in a human-readable way
     *
     * @param routingTable Finger table to use
     * @return the finger table on a string format
     */
    public static String routingTableToString(ChordReference[] routingTable) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < Constants.M_BIT; i++) {
            if (routingTable[i] == null) continue;
            entries.add(String.format("%d-%s", i, routingTable[i]));
        }
        return String.join("\n", entries);
    }

    /**
     * Method to find a successor starting on this peer
     *
     * @param guid Target GUID
     * @return the guid's successor
     */
    public ChordReference findSuccessor(int guid) {
        ChordReference target;
        ChordReference self = new ChordReference(this.address, this.guid);

        if (this.successor() == null) {
            target = self;
        } else if (ChordPeer.between(guid, self.getGuid(), this.successor().getGuid(), false)) {
            target = this.successor();
        } else {
            target = this.closestPrecedingNode(guid);
            target = this.findSuccessor(target, guid);
        }

        return target;
    }

    /**
     * This method asks target for the successor of guid
     *
     * @param target Target to ask the successor of guid
     * @param guid   target guid
     * @return the Successor for guid
     */
    public ChordReference findSuccessor(ChordReference target, int guid) {
        if (target.getGuid() == this.guid) {
            log.debug("Successor is me: {}", target);
            return successor();
        }

        ChordReference self = new ChordReference(this.address, this.guid);
        SSLConnection connection = this.connectToPeer(target.getAddress());
        Message message = new Lookup(self, String.valueOf(guid).getBytes(StandardCharsets.UTF_8));
        log.debug("Sending Lookup message to: {} for {}", target.getGuid(), guid);
        this.send(connection, message);
        LookupReply reply;
        try {
            reply = (LookupReply) this.receiveBlocking(connection, 50);
        } catch (MessageTimeoutException e) {
            log.debug("Could not receive successor");
            // try to close connection
            this.closeConnection(connection);
            return ((Peer) this).getReference();
        }

        this.closeConnection(connection);
        return reply.getReference();
    }

    public synchronized ChordReference getPredecessor() {
        return predecessor;
    }

    /**
     * Method to ask the successor for it's predecessor, this is used for stabilizing the network
     *
     * @return The successor's predecessor
     */
    private ChordReference getPredecessorFromSuccessor() {
        log.debug("Getting predecessor from successor...");

        if (successor() == null) {
            return this.predecessor;
        }

        if (successor().getGuid() == this.guid) {
            log.debug("Predecessor found: " + this.predecessor);
            return this.predecessor;
        }

        SSLConnection connection = this.connectToPeer(successor().getAddress());
        if (connection == null) {
            return null;
        }

        ChordReference self = new ChordReference(this.address, this.guid);
        Message message = new Predecessor(self);
        this.send(connection, message);
        PredecessorReply reply;
        try {
            reply = (PredecessorReply) this.receiveBlocking(connection, 50);
        } catch (MessageTimeoutException e) {
            log.debug("Could not receive predecessor!");
            // try to close connection
            this.closeConnection(connection);
            return this.predecessor;
        }
        this.closeConnection(connection);
        log.debug("Predecessor found: " + reply.getPredecessor());

        return reply.getPredecessor();
    }

    /**
     * Method to stabilize the network, this method checks if there's a peer which sould be this peer's
     * successor, if that proves to be true updates the successor. In any case, it should notify this
     * peer's successor that this peer is its predecessor
     */
    private void stabilize() {
        log.debug("Performing stabilization...");
        ChordReference predecessor;
        try {
            predecessor = this.getPredecessorFromSuccessor();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (predecessor != null) {
            if (between(predecessor.getGuid(), this.guid, successor().getGuid(), true)) {
                log.debug("Successor Updated: " + predecessor);
                this.setSuccessor(predecessor);
            }
        }

        if (successor().getGuid() != this.guid)
            this.notifyPeer(successor(), new ChordReference(this.address, this.guid));

    }

    /**
     * Method to fix a finger on the routing (finger) table
     */
    private void fixFingers() {
        log.debug("Fixing finger:" + nextFinger);
        try {
            int key = this.guid + (int) Math.pow(2, this.nextFinger - 1);
            key = key % Constants.CHORD_MAX_PEERS;

            this.setFinger(this.nextFinger, findSuccessor(successor(), key));
        } catch (Exception e) {

        }
        log.debug("Finger table after fixing:\n" + this.getRoutingTableString());
        this.nextFinger++;
        if (this.nextFinger > Constants.M_BIT) {
            this.nextFinger = 1;
        }
    }

    public synchronized void setPredecessor(ChordReference predecessor) {
        this.predecessor = predecessor;
    }

    /**
     * Method to notify a peer that this peer should be its predecessor
     *
     * @param reference Peer to be notified
     * @param context   Notification context (concerning peer)
     */
    private void notifyPeer(ChordReference reference, ChordReference context) {
        log.debug(String.format("Notifying Peer:%d about Peer:%d", reference.getGuid(), context.getGuid()));

        if (reference.getGuid() == context.getGuid()) {
            log.debug("Skipping notification, the context is the self!");
            return;
        }

        SSLConnection connection = this.connectToPeer(reference.getAddress());
        ChordReference self = new ChordReference(this.address, this.guid);
        Message message = new Notification(self, context.toString().getBytes(StandardCharsets.UTF_8));
        this.send(connection, message);
    }

    /**
     * Method to get the closest preceding node for a GUID
     *
     * @param guid GUID to look for
     * @return the closest preceding node for GUID
     */
    public ChordReference closestPrecedingNode(int guid) {
        for (int i = Constants.M_BIT; i >= 1; i--) {
            if (getFinger(i) == null) continue;
            if (between(getFinger(i).getGuid(), this.guid, guid, true)) {
                return getFinger(i);
            }
        }
        return new ChordReference(this.address, this.guid);
    }

    /**
     * Method to check if a certain key is between two other keys, this method supports right hand side
     * exclusivity by setting a boolean flag
     *
     * @param key       Key to test
     * @param lhs       Left hand Side
     * @param rhs       Right hand Side
     * @param exclusive Exclusivity Flag
     * @return true if key is between the two other keys
     */
    public static boolean between(int key, int lhs, int rhs, boolean exclusive) {
        if (exclusive) {
            return lhs < rhs ? (lhs < key && key < rhs) : (rhs > key || key > lhs);
        } else {
            return lhs < rhs ? (lhs < key && key <= rhs) : (rhs >= key || key > lhs);
        }
    }

    /**
     * Method to check if the predecessor is online
     */
    public void checkPredecessor() {
        if (predecessor != null) {
            SSLConnection connection = this.connectToPeer(predecessor.getAddress());
            if (connection == null) {
                log.info("Could not reach predecessor!");
                this.predecessor = null;
            }
            this.closeConnection(connection);
        }
    }
}
