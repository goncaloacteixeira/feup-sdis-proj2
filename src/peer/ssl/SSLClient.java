package peer.ssl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Random;

public class SSLClient<M> extends SSLCommunication<M> {
    public static final Logger log = LogManager.getLogger(SSLClient.class);

    private final SSLContext context;

    public SSLClient(SSLContext context, Decoder<M> decoder, Encoder<M> encoder, Sizer<M> sizer) {
        super(decoder, encoder, sizer);
        this.context = context;
    }

    public synchronized SSLConnection connectToPeer(InetSocketAddress socketAddress) throws IOException {
        try {
            Thread.sleep(new Random().nextInt(1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        SSLEngine engine = context.createSSLEngine(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
        engine.setUseClientMode(true);

        ByteBuffer appData = ByteBuffer.allocate(256);
        ByteBuffer netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        ByteBuffer peerData = ByteBuffer.allocate(256);
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
        return connection;
    }
}
