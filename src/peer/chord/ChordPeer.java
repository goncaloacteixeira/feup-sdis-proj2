package peer.chord;

import messages.Message;
import messages.chord.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Constants;
import peer.Peer;
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
import java.util.concurrent.*;

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

    public ChordPeer(InetSocketAddress address, boolean boot) throws Exception {
        super(address, boot);
        this.boot = boot;
        this.bootPeer = new ChordReference(address, -1);
    }

    protected void startPeriodicChecks() {
        scheduler.scheduleAtFixedRate(this::fixFingers, 1, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::stabilize, 3, 15, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkPredecessor, 5, 15, TimeUnit.SECONDS);
    }

    public synchronized ChordReference successor() {
        return routingTable[0];
    }

    public synchronized ChordReference getFinger(int position) {
        return routingTable[position - 1];
    }

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

    public boolean join() {
        if (this.boot) {
            this.guid = generateNewKey(this.address);
            this.setSuccessor(new ChordReference(this.address, this.guid));
            log.debug("Peer was started as boot, assigning GUID:" + this.guid);

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

        executorService.submit(() -> this.findSuccessor(bootPeer, this.guid));

        this.closeConnection(bootPeerConnection);
        return true;
    }

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

    public synchronized void setSuccessor(ChordReference finger) {
        this.setFinger(1, finger);
    }

    public static String routingTableToString(ChordReference[] routingTable) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < Constants.M_BIT; i++) {
            if (routingTable[i] == null) continue;
            entries.add(String.format("%d-%s", i, routingTable[i]));
        }
        return String.join("\n", entries);
    }

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
                // start backup for keys higher or equal to predecessor
            }
        }

        if (successor().getGuid() != this.guid)
            this.notifyPeer(successor(), new ChordReference(this.address, this.guid));

    }

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

    public ChordReference closestPrecedingNode(int guid) {
        for (int i = Constants.M_BIT; i >= 1; i--) {
            if (getFinger(i) == null) continue;
            if (between(getFinger(i).getGuid(), this.guid, guid, true)) {
                return getFinger(i);
            }
        }
        return new ChordReference(this.address, this.guid);
    }

    public static boolean between(int key, int lhs, int rhs, boolean exclusive) {
        if (exclusive) {
            return lhs < rhs ? (lhs < key && key < rhs) : (rhs > key || key > lhs);
        } else {
            return lhs < rhs ? (lhs < key && key <= rhs) : (rhs >= key || key > lhs);
        }
    }

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
