package peer.ssl;

import messages.application.Backup;
import messages.application.Get;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SSLServer<M> extends SSLCommunication<M> {
    private final Logger log = LogManager.getLogger(getClass());

    private InetSocketAddress address;
    private final Selector selector;
    private final SSLContext context;
    public boolean active;
    private final List<SSLPeer> observers = new ArrayList<>();

    public SSLServer(SSLContext context, InetSocketAddress address, Decoder<M> decoder, Encoder<M> encoder, Sizer<M> sizer) throws IOException {
        super(decoder, encoder, sizer);

        this.context = context;
        this.address = address;

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

    public void addObserver(SSLPeer peer) {
        this.observers.add(peer);
    }

    public void removeObserver(SSLPeer peer) {
        this.observers.remove(peer);
    }

    public void start() {
        try {
            this._start();
        } catch (Exception e) {
            log.error("Error on start: " + e);
        }
    }

    private void _start() throws Exception {
        log.info("Online and waiting connections on: {}", this.address);

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
                    M message = this.receive((SSLConnection) key.attachment());

                    if (message != null) {
                        this.notify(message, (SSLConnection) key.attachment(), key);
                    }
                }
            }
        }
        log.debug("Shutdown");
    }

    private void notify(M message, SSLConnection connection, SelectionKey key) {
        for (SSLPeer observer : observers) {
            if (message instanceof Backup || message instanceof Get) {
                key.cancel();
            }
            observer.handleNotification(message, connection);
        }
    }

    public void stop() {
        log.debug("[PEER] Peer will be closed...");
        this.active = false;
        this.selector.wakeup();
    }

    private void accept(SelectionKey key) throws Exception {
        log.debug("Received new connection request");

        SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
        socketChannel.configureBlocking(false);

        log.debug("Connected on: {}", socketChannel);

        SSLEngine engine = this.context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(true);

        ByteBuffer appData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        ByteBuffer peerData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer peerNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        SSLConnection connection = new SSLConnection(socketChannel, engine, appData, netData, peerData, peerNetData);

        engine.beginHandshake();

        connection.setHandshake(this.doHandshake(connection));
        socketChannel.register(this.selector, SelectionKey.OP_READ, connection);
    }
}
