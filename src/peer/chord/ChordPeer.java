package peer.chord;

import messages.Message;
import messages.chord.Guid;
import messages.chord.Join;
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

public abstract class ChordPeer extends SSLPeer {
    private final static Logger log = LogManager.getLogger(ChordPeer.class);

    protected int guid = -1;
    protected boolean boot;
    protected ChordReference bootPeer;
    protected ChordReference predecessor;
    protected ChordReference[] routingTable = new ChordReference[Constants.M_BIT];

    public ChordPeer(InetSocketAddress address, boolean boot) throws Exception {
        super(address, boot);
        this.boot = boot;
        this.bootPeer = new ChordReference(address, -1);
    }

    private int lastGUID = 0;

    public ChordReference successor() {
        return routingTable[0];
    }

    public ChordReference getFinger(int position) {
        return routingTable[position - 1];
    }

    public void setFinger(int position, ChordReference finger) {
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
            log.debug("Peer was started as boot, assigning GUID:" + this.guid);
            return;
        }
        log.debug("Trying to join the CHORD circle on: " + this.bootPeer);
        try {
            SSLConnection bootPeerConnection = this.connectToPeer(bootPeer.getAddress(), true);
            if (bootPeerConnection.handshake()) {
                ChordReference self = new ChordReference(this.address, this.guid);

                Message message = new Join(self);
                // send join
                this.sendMessage(bootPeerConnection, message);
                // wait for GUID message
                // GUID task will automatically assign the GUID to the peer
                Guid reply = (Guid) this.readWithReply(bootPeerConnection.getSocketChannel(), bootPeerConnection.getEngine());
                reply.getOperation((Peer) this, bootPeerConnection.getSocketChannel(), bootPeerConnection.getEngine()).run();
                // close connection for now, it will be necessary for the copy request
                this.closeConnection(bootPeerConnection);
                // give some time to process new peer GUID
                Thread.sleep(50);
            } else {
                log.debug("Handshake to boot peer failed");
                this.promote();
            }
        } catch (IOException e) {
            log.debug("Could not connect to boot, promoting itself to boot peer...");
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

    public ChordReference[] getRoutingTable() {
        return routingTable;
    }

    public String getRoutingTableString() {
        return routingTableToString(this.routingTable);
    }

    public static String routingTableToString(ChordReference[] routingTable) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < Constants.M_BIT; i++) {
            if (routingTable[i] == null) continue;
            entries.add(String.format("%d-%s", i, routingTable[i]));
        }
        return String.join("\n", entries);
    }

    public ChordReference lookup(int guid) {
        // todo
        return null;
    }

}
