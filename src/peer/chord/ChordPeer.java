package peer.chord;

import messages.Message;
import messages.chord.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Constants;
import peer.Peer;
import peer.ssl.SSLConnection;
import peer.ssl.SSLPeer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class ChordPeer extends SSLPeer {
    private final static Logger log = LogManager.getLogger(ChordPeer.class);

    protected int guid = -1;
    protected boolean boot;
    protected ChordReference bootPeer;
    protected ChordReference predecessor;
    protected ChordReference[] routingTable = new ChordReference[Constants.M_BIT];
    protected ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(2);
    private int nextFinger = 1;

    public ChordPeer(InetSocketAddress address, boolean boot) throws Exception {
        super(address, boot);
        this.boot = boot;
        this.bootPeer = new ChordReference(address, -1);
    }

    protected void startPeriodicChecks() {
        scheduler.scheduleAtFixedRate(this::fixFingers, 5, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::stabilize, 10, 6, TimeUnit.SECONDS);
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

    public void join() {
        if (this.boot) {
            this.guid = generateNewKey(this.address);
            this.setSuccessor(new ChordReference(this.address, this.guid));
            log.debug("Peer was started as boot, assigning GUID:" + this.guid);
            return;
        }
        log.debug("Trying to join the CHORD circle on: " + this.bootPeer);
        try {
            SSLConnection bootPeerConnection = this.connectToPeer(bootPeer.getAddress(), true);
            ChordReference self = new ChordReference(this.address, this.guid);

            Message message = new Join(self);
            // send join
            this.send(bootPeerConnection, message);
            // wait for GUID message and also Successor
            // GUID task will automatically assign the GUID to the peer
            this.receive(bootPeerConnection).getOperation((Peer) this, bootPeerConnection).run();
        } catch (IOException e) {
            log.debug("Could not connect to boot, promoting itself to boot peer: " + e.getMessage() + " localized: " + e.getLocalizedMessage() + " cause: " + e.getCause());
            e.printStackTrace();
            this.promote();
        } catch (Exception e) {
            e.printStackTrace();
            this.promote();
        }
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
        ChordReference self = new ChordReference(this.address, this.guid);

        if (successor() == null) {
            return self;
        }
        if (between(guid, this.guid, successor().getGuid(), false)) {
            return successor();
        }

        ChordReference closest = closestPrecedingNode(guid);

        try {
            SSLConnection connection = this.connectToPeer(closest.getAddress(), true);
            Message message = new Lookup(self, String.valueOf(guid).getBytes(StandardCharsets.UTF_8));
            this.send(connection, message);
            LookupReply reply = (LookupReply) this.receive(connection);
            this.closeConnection(connection);

            return reply.getReference();
        } catch (IOException e) {
            log.debug("Could not connect to peer");
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            log.error("Could not send message!");
            e.printStackTrace();
            return null;
        }
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
        try {
            SSLConnection connection = this.connectToPeer(successor().getAddress(), true);
            ChordReference self = new ChordReference(this.address, this.guid);
            Message message = new Predecessor(self);
            this.send(connection, message);
            PredecessorReply reply = (PredecessorReply) this.receive(connection);
            this.closeConnection(connection);
            log.debug("Predecessor found: " + reply.getPredecessor());

            return reply.getPredecessor();
        } catch (IOException e) {
            log.debug("Could not connect to peer...");
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
            }
        }
        /* FIXME - notifications are buggy -> probably SSL Peer is poorly implemented */
        if (successor().getGuid() != this.guid)
            this.notifyPeer(successor(), new ChordReference(this.address, this.guid));

    }

    private void fixFingers() {
        log.debug("Fixing finger:" + nextFinger);
        try {
            int key = this.guid + (int) Math.pow(2, this.nextFinger - 1);
            key = key % Constants.CHORD_MAX_PEERS;

            ChordReference reference = findSuccessor(key);
            if (reference == null) {
                log.debug("Something went wrong, successor came null!");
            } else {
                this.setFinger(this.nextFinger, findSuccessor(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
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

        try {
            SSLConnection connection = this.connectToPeer(reference.getAddress(), true);
            ChordReference self = new ChordReference(this.address, this.guid);
            Message message = new Notification(self, context.toString().getBytes(StandardCharsets.UTF_8));
            this.send(connection, message);
            this.closeConnection(connection);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}
