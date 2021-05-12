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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Peer extends ChordPeer implements RemoteInterface {
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
                RemoteInterface stub = (RemoteInterface) UnicastRemoteObject.exportObject(peer, 0);
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

    public void backup(String filename) {
        File file = new File(filename);
        BasicFileAttributes attributes;
        try {
            attributes = Files.getFileAttributeView(file.toPath(), BasicFileAttributeView.class).readAttributes();
            long size = attributes.size();

            String body = String.join("::", Arrays.asList("test", String.valueOf(size), this.getReference().toString()));

            log.info("Starting Backup request...");
            SSLConnection connection = this.connectToPeer(successor().getAddress());
            this.send(connection, new Backup(this.getReference(), body.getBytes(StandardCharsets.UTF_8)));
            log.info("Receiving ACK...");
            Message message = this.receiveBlocking(connection, 100);
            if (!(message instanceof Ack)) return;

            FileChannel fileChannel = FileChannel.open(file.toPath());
            log.info("Sending file...");
            this.sendFile(connection, fileChannel);
            log.info("File sent!");

            log.info("Receiving ACK...");
            message = this.receiveBlocking(connection, 2000);
            if (!(message instanceof Ack)) return;
            log.info("Received ACK!");
            this.closeConnection(connection);
        } catch (IOException | MessageTimeoutException e) {
            e.printStackTrace();
        }
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
