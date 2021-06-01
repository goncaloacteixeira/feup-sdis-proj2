package peer.ssl;

import messages.application.Backup;
import messages.application.Get;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
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

/**
 * Sever Side for the SSL Peer
 *
 * @param <M> Message type to be written/read
 */
public class SSLServer<M> extends SSLCommunication<M> {
    private final Logger log = LogManager.getLogger(getClass());

    private InetSocketAddress address;
    private final Selector selector;
    private final SSLContext context;
    public boolean active;
    private final List<SSLPeer> observers = new ArrayList<>();

    /**
     * Method to start the SSLServer
     *
     * @param context Context used by the server
     * @param address Address used to create the server socket channel
     * @param decoder decoder for the messages received
     * @param encoder encoder for the messages sent
     * @param sizer   sizer for the messages received/sent
     * @throws IOException On error starting the SSL Server
     */
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

    /**
     * Publi method to start the server
     */
    public void start() {
        try {
            this._start();
        } catch (Exception e) {
            log.error("Error on start: " + e);
        }
    }

    /**
     * Method to start the server listening, this is a well-known type of loop for selectable channels, very
     * important for the Non-Blocking operations as the selector handles everything for us.
     *
     * @throws Exception On error starting or while running the server
     */
    private void _start() throws Exception {
        log.info("Online and waiting connections on: {}", this.address);

        while (this.active) {
            selector.select();
            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
            while (selectedKeys.hasNext()) {
                SelectionKey key = selectedKeys.next();
                if (!key.isValid()) {
                    log.error("Key not valid: " + key);
                    continue;
                }
                if (key.isAcceptable()) {
                    this.accept(key);
                } else if (key.isReadable()) {
                    log.debug("About to read with key: {}", key.attachment());

                    M message = this.receive((SSLConnection) key.attachment());

                    if (message != null) {
                        this.notify(message, (SSLConnection) key.attachment(), key);
                    }
                }
                selectedKeys.remove();
            }
        }
        log.debug("Shutdown");
    }

    /**
     * Method to notify the observers (typically just an SSLPeer) that a message was received, and (probably)
     * an action is required, but that's not the server's responsibility, so this message is forwarded to the
     * appropriate objects. Observer Pattern.
     * <p>
     * In addition this method also cancels the keys if the message received is a Backup or a Get, this behaviour
     * is intended so we can control the flow of the restore and backup protocols.
     *
     * @param message    Message received
     * @param connection Connection used
     * @param key        Associated Key
     */
    private void notify(M message, SSLConnection connection, SelectionKey key) {
        for (SSLPeer observer : observers) {
            if (message instanceof Backup || message instanceof Get) {
                key.cancel();
            }
            observer.handleNotification(message, connection);
        }
    }

    /**
     * Method to stop this SSL Server
     */
    public void stop() {
        log.debug("Peer will be closed...");
        this.active = false;
        this.selector.wakeup();
    }

    /**
     * Method to accept a connection, and register this key as readable
     *
     * @param key Key containing the connection to be (potentially) accepted
     * @throws Exception on error accepting the connection
     */
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
        log.debug("Registered Key: {}", connection);
        socketChannel.register(this.selector, SelectionKey.OP_READ, connection);
    }
}
