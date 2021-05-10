package peer.ssl;

import messages.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SSLPeer {
    public static final Logger log = LogManager.getLogger(SSLPeer.class);

    protected InetSocketAddress address;

    private boolean active;
    private final SSLContext context;
    private final Selector selector;
    protected ExecutorService executor = Executors.newFixedThreadPool(16);

    public SSLPeer(InetSocketAddress bootAddress, boolean boot) throws Exception {
        if (boot) {
            this.address = bootAddress;
        } else {
            this.address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
        }

        this.context = SSLContext.getInstance("TLSv1.3");
        context.init(
                this.createKeyManager("resources/server.jks", "sdisg27", "sdisg27"),
                this.createTrustManager("resources/truststore.jks", "sdisg27"),
                new SecureRandom());

        this.selector = SelectorProvider.provider().openSelector();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(this.address);
        // getting the bind address
        this.address = new InetSocketAddress(serverSocketChannel.socket().getInetAddress(), serverSocketChannel.socket().getLocalPort());
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.active = true;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public abstract Object readWithReply(SSLConnection connection) throws Exception;

    public abstract void read(SSLConnection connection) throws Exception;

    public abstract void write(SSLConnection connection, byte[] message) throws IOException;

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

    private void accept(SelectionKey key) throws Exception {
        log.debug("Received new connection request");

        SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
        socketChannel.configureBlocking(false);

        SSLEngine engine = this.context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(true);

        ByteBuffer appData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        ByteBuffer peerData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer peerNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        SSLConnection connection = new SSLConnection(socketChannel, engine, false, appData, netData, peerData, peerNetData);

        engine.beginHandshake();

        if (this.doHandshake(connection)) {
            connection.setHandshake(true);
            socketChannel.register(this.selector, SelectionKey.OP_READ, connection);
        } else {
            socketChannel.close();
            log.debug("Connection closed due to failed handshake");
        }
    }

    public void start() {
        try {
            this._start();
        } catch (Exception e) {
            log.error("Error on start: " + e);
            e.printStackTrace();
        }
    }

    private void _start() throws Exception {
        log.debug("[PEER] Online and waiting connections on: " + this.address);

        while (this.active) {
            selector.select();
            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
            while (selectedKeys.hasNext()) {
                SelectionKey key = selectedKeys.next();
                selectedKeys.remove();
                if (!key.isValid()) {
                    continue;
                }
                if (key.isAcceptable()) {
                    this.accept(key);
                } else if (key.isReadable()) {
                    this.read((SSLConnection) key.attachment());
                }
            }
        }
        log.debug("[PEER] Shutdown");
    }

    public void stop() {
        log.debug("[PEER] Peer will be closed...");
        this.active = false;
        this.executor.shutdown();
        this.selector.wakeup();
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
                                log.error("Failed to send CLOSE message due to socket channel's failure: " + Arrays.toString(e.getStackTrace()));
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

    public void sendMessage(SSLConnection connection, Message message) {
        try {
            this.write(connection, message.encode());
        } catch (IOException e) {
            log.debug("Could not write message: " + e.getMessage() + " localized: " + e.getLocalizedMessage() + " cause: " + e.getCause());
        }
    }
}
