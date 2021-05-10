package peer.ssl;

import messages.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SSLClient<M> {
    public static final Logger log = LogManager.getLogger(SSLClient.class);

    protected SSLContext context;

    private final Decoder<M> decoder;
    private final Encoder<M> encoder;
    protected ExecutorService executor = Executors.newFixedThreadPool(16);

    public SSLClient(Decoder<M> decoder, Encoder<M> encoder) {
        this.decoder = decoder;
        this.encoder = encoder;
    }

    public M receive(SSLConnection connection) throws Exception {
        log.debug("[PEER] Reading data...");

        SSLEngine engine = connection.getEngine();

        connection.getPeerNetData().clear();

        int bytesRead = connection.getSocketChannel().read(connection.getPeerNetData());
        if (bytesRead > 0) {
            connection.getPeerNetData().flip();

            M message = null;

            while (connection.getPeerNetData().hasRemaining()) {
                connection.getPeerData().clear();
                SSLEngineResult result = engine.unwrap(connection.getPeerNetData(), connection.getPeerData());

                switch (result.getStatus()) {
                    case OK:
                        connection.getPeerData().flip();

                        message = this.decoder.decode(connection.getPeerData());
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

    public void send(SSLConnection connection, M message) throws IOException {
        log.debug("[PEER] Writing data...");

        SSLEngine engine = connection.getEngine();

        connection.getAppData().clear();
        encoder.encode(message, connection.getAppData());
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
                    log.debug("Message Sent: " + message);
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

    public SSLConnection connectToPeer(InetSocketAddress socketAddress, boolean blocking) throws IOException {
        SSLEngine engine = context.createSSLEngine(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
        engine.setUseClientMode(true);

        ByteBuffer appData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        ByteBuffer peerData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
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
        log.debug("Channel: " + socketChannel);
        return connection;
    }

    public void closeConnection(SSLConnection connection) throws IOException {
        connection.getEngine().closeOutbound();
        this.doHandshake(connection);
        connection.getSocketChannel().close();
    }

    protected boolean doHandshake(SSLConnection connection) throws IOException {
        log.debug("Starting handshake...");

        SSLEngineResult result;
        SSLEngineResult.HandshakeStatus handshakeStatus;

        SSLEngine engine = connection.getEngine();
        SocketChannel socketChannel = connection.getSocketChannel();

        int appBufferSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer applicationData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerApplicationData = ByteBuffer.allocate(appBufferSize);
        connection.getNetData().clear();
        connection.getPeerNetData().clear();

        handshakeStatus = engine.getHandshakeStatus();
        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    if (socketChannel.read(connection.getPeerNetData()) < 0) {
                        if (engine.isInboundDone() && engine.isOutboundDone()) {
                            return false;
                        }
                        try {
                            engine.closeInbound();
                        } catch (SSLException e) {
                            log.error("Engine was forced to close inbound. Message: " + e.getMessage());
                        }
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    connection.getPeerNetData().flip();
                    try {
                        do {
                            result = engine.unwrap(connection.getPeerNetData(), peerApplicationData);
                        } while (connection.getPeerNetData().hasRemaining() || result.bytesProduced() > 0);

                        connection.getPeerNetData().compact();
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException e) {
                        log.error("Error processing data, will try to close gracefully: " + Arrays.toString(e.getStackTrace()));
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()) {
                        case OK:
                            break;
                        case BUFFER_OVERFLOW:
                            peerApplicationData = enlargeApplicationBuffer(engine, peerApplicationData);
                            break;
                        case BUFFER_UNDERFLOW:
                            connection.setPeerNetData(handleBufferUnderflow(engine, connection.getPeerNetData()));
                            break;
                        case CLOSED:
                            if (engine.isOutboundDone()) {
                                return false;
                            } else {
                                engine.closeOutbound();
                                handshakeStatus = engine.getHandshakeStatus();
                                break;
                            }
                        default:
                            throw new IllegalStateException("Invalid SSL Status: " + result.getStatus());
                    }
                    break;
                case NEED_WRAP:
                    connection.getNetData().clear();
                    try {
                        result = engine.wrap(applicationData, connection.getNetData());
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException e) {
                        log.error("Error processing data, will try to close gracefully: " + e + " localized: " + e.getLocalizedMessage() + " cause: " + e.getCause());
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()) {
                        case OK:
                            connection.getNetData().flip();
                            while (connection.getNetData().hasRemaining()) {
                                socketChannel.write(connection.getNetData());
                            }
                            break;
                        case BUFFER_OVERFLOW:
                            connection.setNetData(enlargePacketBuffer(engine, connection.getNetData()));
                            break;
                        case CLOSED:
                            try {
                                connection.getNetData().flip();
                                while (connection.getNetData().hasRemaining()) {
                                    socketChannel.write(connection.getNetData());
                                }
                                connection.getPeerNetData().clear();
                            } catch (Exception e) {
                                log.trace("Failed to send CLOSE message due to socket channel's failure: " + e.getMessage());
                                handshakeStatus = engine.getHandshakeStatus();
                            }
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL Status: " + result.getStatus());
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        executor.execute(task);
                    }
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                case FINISHED:
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL Status: " + handshakeStatus);
            }
        }
        log.debug("Handshake Valid!");
        return true;
    }

    protected ByteBuffer enlargePacketBuffer(SSLEngine engine, ByteBuffer buffer) {
        return enlargeBuffer(buffer, engine.getSession().getPacketBufferSize());
    }

    protected ByteBuffer enlargeApplicationBuffer(SSLEngine engine, ByteBuffer buffer) {
        return enlargeBuffer(buffer, engine.getSession().getApplicationBufferSize());
    }

    protected ByteBuffer enlargeBuffer(ByteBuffer buffer, int size) {
        if (size > buffer.capacity()) {
            buffer = ByteBuffer.allocate(size);
        } else {
            buffer = ByteBuffer.allocate(buffer.capacity() * 2);
        }
        return buffer;
    }

    protected ByteBuffer handleBufferUnderflow(SSLEngine engine, ByteBuffer buffer) {
        if (engine.getSession().getPacketBufferSize() < buffer.limit()) {
            return buffer;
        }
        ByteBuffer replaceBuffer = enlargePacketBuffer(engine, buffer);
        buffer.flip();
        replaceBuffer.put(buffer);
        return replaceBuffer;
    }

    protected void handleEndOfStream(SSLConnection connection) throws IOException {
        try {
            connection.getEngine().closeInbound();
        } catch (Exception e) {
            log.error("This engine was forced to close due to end of stream without receiving the notification from peer");
        }
        closeConnection(connection);
    }

    protected KeyManager[] createKeyManager(String filepath, String keystorePassword, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(filepath)) {
            keyStore.load(is, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());
        return kmf.getKeyManagers();
    }

    protected TrustManager[] createTrustManager(String filepath, String keystorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(filepath)) {
            trustStore.load(is, keystorePassword.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

}
