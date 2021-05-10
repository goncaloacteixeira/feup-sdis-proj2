package peer;

import messages.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Peer extends ChordPeer {
    private final static Logger log = LogManager.getLogger(Peer.class);
    private String sap;
    private final ExecutorService executorService = Executors.newFixedThreadPool(16);

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
            new Thread(peer::start).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        this.join();
        this.startPeriodicChecks();
        super.start();
    }

    public Peer(InetSocketAddress address, boolean boot, String sap) throws Exception {
        super(address, boot);
        this.sap = sap;
    }

    public ChordReference getReference() {
        return new ChordReference(this.address, this.guid);
    }
}
