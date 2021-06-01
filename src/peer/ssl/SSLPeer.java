package peer.ssl;

import messages.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Constants;
import peer.Peer;
import peer.Utils;
import peer.backend.PeerInternalState;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Abstract Class intended to be used by an higher layer, containing application data to be transferred using
 * this implementation. In this case, SSLPeer is extended by ChordPeer which transmits it's messages related to
 * the Chord Network using SSL, and more precisely SSLEngine and Socket Channels.
 *
 * This SSL Peer contains an SSLServer for incoming connections and an SSLClient for outgoing connections and requests.
 */
public abstract class SSLPeer {
    public static final Logger log = LogManager.getLogger(SSLPeer.class);

    protected InetSocketAddress address;
    private final SSLContext context;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final SSLServer<Message> server;
    private final SSLClient<Message> client;

    protected PeerInternalState internalState;

    private static Message decode(ByteBuffer byteBuffer) {
        byte[] buffer;
        int size = byteBuffer.remaining();
        if (byteBuffer.hasArray()) {
            buffer = Arrays.copyOfRange(byteBuffer.array(),
                    byteBuffer.arrayOffset() + byteBuffer.position(),
                    size);
        } else {
            buffer = new byte[byteBuffer.remaining()];
            byteBuffer.duplicate().get(buffer);
        }
        return Message.parse(buffer, size);
    }

    private static void encode(Message message, ByteBuffer byteBuffer) {
        byteBuffer.put(message.encode());
    }

    private static int sizer(Message message) {
        return message.encode().length;
    }

    /**
     * Method to start the SSLPeer, this method uses the boot peer address if the peer is signaled to be
     * boot peer or creates a new server socket channel with the first free port.
     *
     * @param bootAddress Boot Peer address
     * @param boot        boot flag
     * @throws Exception on error starting the peer
     */
    public SSLPeer(InetSocketAddress bootAddress, boolean boot) throws Exception {
        if (boot) {
            this.address = bootAddress;
        } else {
            this.address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
        }

        this.context = SSLContext.getInstance("TLSv1.2");
        context.init(
                SSLCommunication.createKeyManager("resources/peer.jks", "sdisg27", "sdisg27"),
                SSLCommunication.createTrustManager("resources/truststore.jks", "sdisg27"),
                new SecureRandom());

        this.server = new SSLServer<>(context, this.address, SSLPeer::decode, SSLPeer::encode, SSLPeer::sizer);
        this.client = new SSLClient<>(context, SSLPeer::decode, SSLPeer::encode, SSLPeer::sizer);

        this.address = this.server.getAddress();
        this.server.addObserver(this);
        new Thread(this.server::start).start();
    }

    public boolean isActive() {
        return server.active;
    }

    /**
     * Higher implementation for peer connections, the error handling is performed here, if any
     * error occurs the higher layer will receive null for the connection stating that the connection
     * could not be established.
     *
     * @param address Address to connect to
     * @return an SSLConnection if the connection was successful or null otherwise
     */
    public synchronized SSLConnection connectToPeer(InetSocketAddress address) {
        try {
            log.debug("Connecting to peer: " + address);
            return this.client.connectToPeer(address);
        } catch (IOException e) {
            log.trace("Could not connect to peer {}, exception: {}", address, e.getMessage());
        }
        return null;
    }

    /**
     * Higher method to send a Message, returns true on success
     */
    public boolean send(SSLConnection connection, Message message) {
        try {
            this.client.send(connection, message);
            return true;
        } catch (IOException e) {
            log.trace("Could not send message to peer, exception: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Higher method to receive a message, returns a message on success or null otherwise
     */
    public Message receive(SSLConnection connection) {
        try {
            return this.client.receive(connection);
        } catch (Exception e) {
            log.trace("Could not receive message from peer, exception: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Method to receive a message with blocking mode, this method also uses a time to read parameter so it
     * can wait different periods depending on the client's needs
     *
     * @param connection Connection to be used
     * @param timeToRead Time to read used
     * @return the message read
     * @throws MessageTimeoutException on timeout reading the message.
     */
    public Message receiveBlocking(SSLConnection connection, int timeToRead) throws MessageTimeoutException {
        Message reply;
        int attempt = 0;
        connection.getPeerData().clear();
        reply = this.receive(connection);
        /*while ((reply = this.receive(connection)) == null && attempt < 50) {
            attempt++;
            try {
                Thread.sleep(timeToRead * 3L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }*/
        if (reply == null) {
            throw new MessageTimeoutException("Message took too long to receive!");
        }

        return reply;
    }

    /**
     * Method to receive a file with a known size
     *
     * @param connection  Connection to be used
     * @param fileChannel File Channel to be used
     * @param size        File's Size in Bytes
     */
    public void receiveFile(SSLConnection connection, FileChannel fileChannel, long size) {
        try {
            final long started = System.currentTimeMillis();

            long total = 0;
            connection.setPeerData(ByteBuffer.allocate(Constants.CHUNK_SIZE));
            while (true) {
                long bytes;
                bytes = this.client.receiveFile(connection, fileChannel);
                total += bytes;

                System.out.printf("Receiving (%s): %s (%s)\r",
                        Utils.prettySize(size),
                        Utils.progressBar(total, size),
                        Utils.rate(started, System.currentTimeMillis(), total)
                );
                if (bytes < 0 || total == size) {
                    System.out.printf("Received (%s): %s\n", Utils.prettySize(size), Utils.progressBar(total, size));
                    return;
                }
            }
        } catch (IOException e) {
            log.error("Error receiving file: {}", e.getMessage());
        }
    }

    /**
     * Method to send a file to another peer
     *
     * @param connection  connection to be Used
     * @param fileChannel File Channel Used to read the file from the system
     */
    public void sendFile(SSLConnection connection, FileChannel fileChannel) {
        try {
            this.client.sendFile(connection, fileChannel);
        } catch (IOException | InterruptedException e) {
            log.error("Error sending file: {}", e.getMessage());
        }
    }

    /**
     * Method to handle a notification, this is called by the server when a new message is received
     *
     * @param message    Message Received
     * @param connection Connection used
     */
    public void handleNotification(Object message, SSLConnection connection) {
        executor.submit(((Message) message).getOperation((Peer) this, connection));
    }

    /**
     * Higher method to close the connection
     *
     * @param connection connection used
     * @return true if the closing operation was successful, false otherwise
     */
    public boolean closeConnection(SSLConnection connection) {
        try {
            this.client.closeConnection(connection);
            return true;
        } catch (IOException e) {
            log.trace("Could not close connection to peer, exception: {}", e.getMessage());
        }
        return false;
    }
}
