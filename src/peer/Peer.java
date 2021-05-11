package peer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class Peer extends ChordPeer {
    private final static Logger log = LogManager.getLogger(Peer.class);
    private String sap;

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
            log.debug("Peer Initiated");
            new Thread(peer::start).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        this.join();
        this.startPeriodicChecks();
    }

    public Peer(InetSocketAddress address, boolean boot, String sap) throws Exception {
        super(address, boot);
        this.sap = sap;
    }

    public ChordReference getReference() {
        return new ChordReference(this.address, this.guid);
    }
}
