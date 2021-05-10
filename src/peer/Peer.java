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

    @Override
    public void read(SSLConnection connection) throws Exception {
        Message message = (Message) readWithReply(connection);
        if (message != null)
            executorService.submit(message.getOperation(this, connection));
    }

    @Override
    public Object readWithReply(SSLConnection connection) throws Exception {
        log.debug("[PEER] Reading data...");

        SSLEngine engine = connection.getEngine();

        connection.getPeerNetData().clear();

        int bytesRead = connection.getSocketChannel().read(connection.getPeerNetData());
        if (bytesRead > 0) {
            connection.getPeerNetData().flip();

            Message message = null;

            while (connection.getPeerNetData().hasRemaining()) {
                connection.getPeerData().clear();
                SSLEngineResult result = engine.unwrap(connection.getPeerNetData(), connection.getPeerData());

                switch (result.getStatus()) {
                    case OK:
                        connection.getPeerData().flip();

                        byte[] buffer;
                        int size = connection.getPeerData().remaining();
                        if (connection.getPeerData().hasArray()) {
                            buffer = Arrays.copyOfRange(connection.getPeerData().array(),
                                    connection.getPeerData().arrayOffset() + connection.getPeerData().position(),
                                    size);
                        } else {
                            buffer = new byte[connection.getPeerData().remaining()];
                            connection.getPeerData().duplicate().get(buffer);
                        }
                        System.out.println(Arrays.toString(buffer));
                        message = Message.parse(buffer, size);
                        log.debug("Message received: " + message);
                        break;
                    case BUFFER_OVERFLOW:
                        connection.setPeerData(this.enlargeApplicationBuffer(engine, connection.getPeerData()));
                        break;
                    case BUFFER_UNDERFLOW:
                        connection.setPeerNetData(this.handleBufferUnderflow(engine, connection.getPeerNetData()));
                        break;
                    case CLOSED:
                        log.debug("The other peer requests closing the connection");
                        // this.closeConnection(connection);
                        connection.getEngine().closeOutbound();
                        connection.getSocketChannel().close();
                        log.debug("Connection closed!");
                        return null;
                    default:
                        throw new IllegalStateException("Invalid SSL Status: " + result.getStatus());
                }
            }

            return message;
        } else if (bytesRead < 0) {
            log.error("Received EOS. Trying to close connection...");
            handleEndOfStream(connection);
            log.debug("Connection closed!");
        }

        return null;
    }

    @Override
    public void write(SSLConnection connection, byte[] message) throws IOException {
        log.debug("[PEER] Writing data...");

        SSLEngine engine = connection.getEngine();

        connection.getAppData().clear();
        connection.getAppData().put(message);
        connection.getAppData().flip();
        while (connection.getAppData().hasRemaining()) {
            connection.getNetData().clear();
            SSLEngineResult result = engine.wrap(connection.getAppData(), connection.getNetData());
            switch (result.getStatus()) {
                case OK:
                    connection.getNetData().flip();
                    int bytesWritten = 0;
                    while (connection.getNetData().hasRemaining()) {
                        bytesWritten += connection.getSocketChannel().write(connection.getNetData());
                    }
                    log.debug("Message Sent: " + Message.parse(message, message.length));
                    break;
                case BUFFER_OVERFLOW:
                    connection.setNetData(enlargePacketBuffer(engine, connection.getNetData()));
                    break;
                case CLOSED:
                    this.closeConnection(connection);
                    return;
                default:
                    throw new IllegalStateException("Invalid SSL Status: " + result.getStatus());
            }
        }
    }

    public ChordReference getReference() {
        return new ChordReference(this.address, this.guid);
    }
}
