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
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SSLPeer {
    public static final Logger log = LogManager.getLogger(SSLPeer.class);

    protected InetSocketAddress address;
    protected ByteBuffer applicationData;
    protected ByteBuffer netData;
    protected ByteBuffer peerApplicationData;
    protected ByteBuffer peerNetData;
    private boolean active;
    private final SSLContext context;
    private final Selector selector;
    protected ExecutorService executor = Executors.newSingleThreadExecutor();

    public SSLPeer(InetSocketAddress bootAddress, boolean boot) throws Exception {
        if (boot) {
            this.address = bootAddress;
        } else {
            this.address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
        }

        this.context = SSLContext.getInstance("TLSv1.2");
        context.init(
                this.createKeyManager("resources/server.jks", "sdisg27", "sdisg27"),
                this.createTrustManager("resources/truststore.jks", "sdisg27"),
                new SecureRandom());

        SSLSession dummy = context.createSSLEngine().getSession();
        this.applicationData = ByteBuffer.allocate(dummy.getApplicationBufferSize());
        this.netData = ByteBuffer.allocate(dummy.getPacketBufferSize());
        this.peerApplicationData = ByteBuffer.allocate(dummy.getApplicationBufferSize());
        this.peerNetData = ByteBuffer.allocate(dummy.getPacketBufferSize());
        dummy.invalidate();

        this.selector = SelectorProvider.provider().openSelector();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(this.address);
        // getting the bind address
        this.address = new InetSocketAddress(serverSocketChannel.socket().getInetAddress(), serverSocketChannel.socket().getLocalPort());
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.active = true;
    }

    public abstract Message readWithReply(SocketChannel socketChannel, SSLEngine engine) throws Exception;

    public abstract void read(SocketChannel socketChannel, SSLEngine engine) throws Exception;

    public abstract void write(SocketChannel socketChannel, SSLEngine engine, byte[] message) throws IOException;

    public SSLConnection connectToPeer(InetSocketAddress socketAddress, boolean blocking) throws IOException {
        SSLEngine engine = context.createSSLEngine(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
        engine.setUseClientMode(true);

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(blocking);
        socketChannel.connect(socketAddress);
        while (!socketChannel.finishConnect()) {
            // can do something here...
        }
        engine.beginHandshake();

        log.debug("Connected to Peer!");
        return new SSLConnection(socketChannel, engine, this.doHandshake(socketChannel, engine));
    }

    public void closeConnection(SSLConnection connection) throws IOException {
        connection.getEngine().closeOutbound();
        connection.getSocketChannel().close();
        log.debug("Connection Successfully Closed!");
    }

    private void accept(SelectionKey key) throws Exception {
        log.debug("[PEER] Received new connection request");

        SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
        socketChannel.configureBlocking(false);

        SSLEngine engine = this.context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(true);
        engine.beginHandshake();

        if (this.doHandshake(socketChannel, engine)) {
            socketChannel.register(this.selector, SelectionKey.OP_READ, engine);
        } else {
            socketChannel.close();
            log.debug("Connection closed due to failed handshake");
        }
    }

    protected void start() {
        try {
            this._start();
        } catch (Exception e) {
            log.error("Error on start: " + e);
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
                    this.read((SocketChannel) key.channel(), (SSLEngine) key.attachment());
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

    protected boolean doHandshake(SocketChannel socketChannel, SSLEngine engine) throws IOException {
        log.debug("Starting handshake...");

        SSLEngineResult result;
        SSLEngineResult.HandshakeStatus handshakeStatus;

        int appBufferSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer applicationData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerApplicationData = ByteBuffer.allocate(appBufferSize);
        netData.clear();
        peerNetData.clear();

        handshakeStatus = engine.getHandshakeStatus();
        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    if (socketChannel.read(peerNetData) < 0) {
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
                    peerNetData.flip();
                    try {
                        result = engine.unwrap(peerNetData, peerApplicationData);
                        peerNetData.compact();
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException e) {
                        log.error("Error processing data, will try to close gracefully...");
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
                            peerNetData = handleBufferUnderflow(engine, peerNetData);
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
                    netData.clear();
                    try {
                        result = engine.wrap(applicationData, netData);
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException e) {
                        log.error("Error processing data, will try to close gracefully...");
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()) {
                        case OK:
                            netData.flip();
                            while (netData.hasRemaining()) {
                                socketChannel.write(netData);
                            }
                            break;
                        case BUFFER_OVERFLOW:
                            netData = enlargePacketBuffer(engine, netData);
                            break;
                        case CLOSED:
                            try {
                                netData.flip();
                                while (netData.hasRemaining()) {
                                    socketChannel.write(netData);
                                }
                                peerNetData.clear();
                            } catch (Exception e) {
                                log.error("Failed to send CLOSE message due to socket channel's failure.");
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
                default:
                    throw new IllegalStateException("Invalid SSL Status: " + handshakeStatus);
            }
        }
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

    protected void closeConnection(SocketChannel socketChannel, SSLEngine engine) throws IOException {
        engine.closeOutbound();
        doHandshake(socketChannel, engine);
        socketChannel.close();
    }

    protected void handleEndOfStream(SocketChannel socketChannel, SSLEngine engine) throws IOException {
        try {
            engine.closeInbound();
        } catch (Exception e) {
            log.error("This engine was forced to close due to end of stream without receiving the notification from peer");
        }
        closeConnection(socketChannel, engine);
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

    public void sendMessage(SSLConnection bootPeerConnection, Message message) {
        try {
            this.write(bootPeerConnection.getSocketChannel(), bootPeerConnection.getEngine(), message.encode());
        } catch (IOException e) {
            log.debug("Could not write message: " + e.getMessage());
        }
    }
}
