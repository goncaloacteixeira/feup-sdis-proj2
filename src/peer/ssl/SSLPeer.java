package peer.ssl;

import messages.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;

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

public abstract class SSLPeer extends SSLClient<Message> {
    public static final Logger log = LogManager.getLogger(SSLPeer.class);

    protected InetSocketAddress address;

    private boolean active;
    private final SSLContext context;
    private final Selector selector;
    protected ExecutorService executor = Executors.newFixedThreadPool(16);

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

    public SSLPeer(InetSocketAddress bootAddress, boolean boot) throws Exception {
        super(SSLPeer::decode, SSLPeer::encode);

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

        this.selector = SelectorProvider.provider().openSelector();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(this.address);
        // getting the bind address
        this.address = new InetSocketAddress(serverSocketChannel.socket().getInetAddress(), serverSocketChannel.socket().getLocalPort());
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        super.context = context;
        this.active = true;
    }

    public InetSocketAddress getAddress() {
        return address;
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

        connection.setHandshake(this.doHandshake(connection));
        socketChannel.register(this.selector, SelectionKey.OP_READ, connection);
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
                    Message message = this.receive((SSLConnection) key.attachment());

                    if (message != null) {
                        message.getOperation((Peer) this, (SSLConnection) key.attachment()).run();
                    }
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
