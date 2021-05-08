package peer.chord;

import messages.Message;
import messages.chord.Join;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Constants;
import peer.ssl.SSLConnection;
import peer.ssl.SSLPeer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;

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
        this.bootPeer = new ChordReference(address, 0);
    }

    private int lastGUID = 0;

    public ChordReference successor() {
        return routingTable[0];
    }

    public ChordReference getFinger(int position) {
        return routingTable[position];
    }

    public void join() {
        if (this.boot) {
            log.debug("Peer was started as boot, assigning GUID:0...");
            this.guid = 0;
            return;
        }
        log.debug("Trying to join the CHORD circle on: " + this.bootPeer);
        try {
            SSLConnection bootPeerConnection = this.connectToPeer(bootPeer.getAddress(), true);
            if (bootPeerConnection.handshake()) {
                ChordReference self = new ChordReference(this.address, this.guid);
                Message message = new Join(self, self);
                // send join
                this.sendMessage(bootPeerConnection, message);
                // wait for GUID message
                // GUID task will automatically assign the GUID to the peer
                this.read(bootPeerConnection.getSocketChannel(), bootPeerConnection.getEngine());
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

    private void promote() {
        this.guid = 0;
        this.bootPeer = new ChordReference(this.address, this.guid);
        this.boot = true;
    }

    public int generateNewKey() {
        for (int i = ++this.lastGUID; i < Constants.CHORD_MAX_PEERS; i++) {
            if (this.lookup(i) == null) {
                this.lastGUID = i;
                return i;
            }
        }
        return -1;
    }

    public ChordReference lookup(int guid) {
        // todo
        return null;
    }

}
