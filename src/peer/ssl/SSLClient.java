package peer.ssl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SSLClient<M> extends SSLCommunication<M> {
    public static final Logger log = LogManager.getLogger(SSLClient.class);

    private final SSLContext context;

    public SSLClient(SSLContext context, Decoder<M> decoder, Encoder<M> encoder) {
        super(decoder, encoder);
        this.context = context;
    }

    public SSLConnection connectToPeer(InetSocketAddress socketAddress, boolean blocking) throws IOException {
        SSLEngine engine = context.createSSLEngine(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
        engine.setUseClientMode(true);

        ByteBuffer appData = ByteBuffer.allocate(256);
        ByteBuffer netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        ByteBuffer peerData = ByteBuffer.allocate(256);
        ByteBuffer peerNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(blocking);
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
        return connection;
    }
}
