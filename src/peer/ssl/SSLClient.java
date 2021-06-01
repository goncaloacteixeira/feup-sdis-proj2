package peer.ssl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Random;

/**
 * Client Side for an SSLPeer, this class is responsible for knowing how to make a connection to another peer.
 * Of course, this class extends the SSLCommunication so it can the read/write/handshake operations.
 *
 * @param <M> The type of messages that should be expected to be written and read from/by the socket, this
 *            class could be String, Integer, etc. but for this project it will be Message.
 * @see messages.Message
 */
public class SSLClient<M> extends SSLCommunication<M> {
    public static final Logger log = LogManager.getLogger(SSLClient.class);

    private final SSLContext context;

    /**
     * Constructor for a SSL Client
     *
     * @param context SSLContext used to secure the connection
     * @param decoder Message's Decoder used on the read operations
     * @param encoder Message's Encoder used on the write operations
     * @param sizer   Message's Sizer used to determinate how long is a message
     */
    public SSLClient(SSLContext context, Decoder<M> decoder, Encoder<M> encoder, Sizer<M> sizer) {
        super(decoder, encoder, sizer);
        this.context = context;
    }

    /**
     * Method to connect to another peer, this method also performs the handshake before proceeding with the
     * data exchange
     *
     * @param socketAddress Address to connect the socket to
     * @return an SSLConnection if the connection was successful
     * @throws IOException on Error connecting to the peer
     */
    public synchronized SSLConnection connectToPeer(InetSocketAddress socketAddress) throws IOException {
        try {
            Thread.sleep(new Random().nextInt(1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        SSLEngine engine = context.createSSLEngine(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
        engine.setUseClientMode(true);

        ByteBuffer appData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        ByteBuffer peerData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer peerNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(socketAddress);

        SSLConnection connection = new SSLConnection(socketChannel, engine, false, appData, netData, peerData, peerNetData);

        while (!socketChannel.finishConnect()) {
            // can do something here...
        }
        engine.beginHandshake();
        try {
            connection.setHandshake(this.doHandshake(connection));
        } catch (IOException e) {
            log.error("Could not validate handshake!");
            return connection;
        }

        log.debug("Connected to Peer on: " + socketAddress);

        try {
            log.debug("Giving some time for the connection to be established...");
            Thread.sleep(100);
        } catch (InterruptedException ignored) { }

        return connection;
    }
}
