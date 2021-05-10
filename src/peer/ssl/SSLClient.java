package peer.ssl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SSLClient<M> {
    public static final Logger log = LogManager.getLogger(SSLClient.class);

    private final SSLContext context;

    private final Decoder<M> decoder;
    private final Encoder<M> encoder;
    protected ExecutorService executor = Executors.newFixedThreadPool(16);

    public SSLClient(SSLContext context, Decoder<M> decoder, Encoder<M> encoder) throws Exception {
        this.decoder = decoder;
        this.encoder = encoder;
        this.context = context;
    }

    public SSLConnection connectToPeer(InetSocketAddress socketAddress, boolean blocking) throws IOException {
        SSLEngine engine = context.createSSLEngine();
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
        this.checkHandshakeStatus(connection);

        return connection;
    }

    public void send(M message, SSLConnection connection) throws IOException {
        this.encoder.encode(message, connection.getAppData());
        doWrap(connection);
    }

    public M receive(SSLConnection connection) throws IOException {
        while (true) {
            connection.getPeerData().flip();
            try {
                final M message = decoder.decode(connection.getPeerData());
                if (message != null) {
                    return message;
                }
            } finally {
                connection.getPeerData().compact();
            }
            doUnwrap(connection);
        }
    }

    private void checkHandshakeStatus(SSLConnection connection) throws IOException {
        checkHandshakeStatus(connection, connection.getEngine().getHandshakeStatus());
    }

    private void checkHandshakeStatus(SSLConnection connection, SSLEngineResult.HandshakeStatus handshakeStatus) throws IOException {
        switch (handshakeStatus) {
            case NOT_HANDSHAKING:
                // No action necessary
                return;
            case FINISHED:
                log.debug("Initial SSL handshake finished - using protocol: " + connection.getEngine().getSession().getProtocol());
                return;
            case NEED_WRAP:
                doWrap(connection);
                break;

            case NEED_UNWRAP:
                doUnwrap(connection);
                break;

            case NEED_TASK:
                // The SSLEngine has some task(s) that must be run before continuing
                Runnable task;
                while ((task = connection.getEngine().getDelegatedTask()) != null) {
                    executor.submit(task);
                }
                checkHandshakeStatus(connection);
                break;

            default:
                throw new IllegalStateException("Invalid SSL handshake status: " + handshakeStatus);
        }
    }

    private void doWrap(SSLConnection connection) throws IOException {
        connection.getAppData().flip();
        final SSLEngineResult result;
        try {
            result = connection.getEngine().wrap(connection.getAppData(), connection.getNetData());
        } catch (SSLException e) {
            log.trace("Exception while calling SSLEngine.wrap()", e);
            closeChannel(connection);
            return;
        }
        connection.getAppData().compact();
        log.trace("Result of SSLEngine.wrap(): {}", result);

        // It is important to perform the appropriate action for the result status and the result handshake status.
        // NOTE: The handshake status FINISHED is a transient status. It is important to look at the status in the
        // result and not just call sslEngine.getStatus() because the FINISHED status will only be reported once,
        // in the result returned by wrap() or unwrap().

        final SSLEngineResult.Status status = result.getStatus();
        switch (status) {
            case OK:
                flush(connection);
                checkHandshakeStatus(connection);
                // Repeat wrap if there is still data in the application output buffer
                if (connection.getAppData().position() > 0) {
                    doWrap(connection);
                }
                break;
            case CLOSED:
                flush(connection);
                checkHandshakeStatus(connection);
                close(connection);
                break;
            case BUFFER_OVERFLOW:
                // The network output buffer does not have enough space, re-allocate and retry wrap
                // (NOTE: packet buffer size as reported by the SSL session might change dynamically)
                connection.setNetData(ensureRemaining(connection.getNetData(), connection.getEngine().getSession().getPacketBufferSize()));
                doWrap(connection);
                break;
            default:
                throw new IllegalStateException("Invalid SSL status: " + status);
        }
    }

    private void flush(SSLConnection connection) throws IOException {
        // Flush the content of the network output buffer to the socket channel
        connection.getNetData().flip();
        try {
            while (connection.getNetData().hasRemaining()) {
                connection.getSocketChannel().write(connection.getNetData());
            }
        } finally {
            connection.getNetData().compact();
        }
    }

    private void doUnwrap(SSLConnection connection) throws IOException {
        if (connection.getPeerNetData().position() == 0) {
            // The network input buffer is empty; read data from the channel before doing the unwrap
            final int count = connection.getSocketChannel().read(connection.getPeerNetData());
            if (count == -1) {
                handleEndOfStream(connection);
                return;
            }
        }

        connection.getPeerNetData().flip();
        final SSLEngineResult result;
        try {
            result = connection.getEngine().unwrap(connection.getPeerNetData(), connection.getPeerData());
        } catch (SSLException e) {
            log.warn("Exception while calling SSLEngine.unwrap()", e);
            closeChannel(connection);
            return;
        }
        connection.getPeerNetData().compact();
        log.trace("Result of SSLEngine.unwrap(): {}", result);

        // It is important to perform the appropriate action for the result status and the result handshake status.
        // NOTE: The handshake status FINISHED is a transient status. It is important to look at the status in the
        // result and not just call sslEngine.getStatus() because the FINISHED status will only be reported once,
        // in the result returned by wrap() or unwrap().

        final SSLEngineResult.Status status = result.getStatus();
        switch (status) {
            case OK:
                checkHandshakeStatus(connection);
                break;

            case CLOSED:
                checkHandshakeStatus(connection);
                close(connection);
                break;

            case BUFFER_UNDERFLOW:
                // The network input buffer might not have enough space, re-allocate if necessary
                // (NOTE: packet buffer size as reported by the SSL session might change dynamically)
                connection.setPeerNetData(ensureRemaining(connection.getPeerNetData(), connection.getEngine().getSession().getPacketBufferSize()));

                // Read data from the channel, retry unwrap if not end-of-stream
                final int count = connection.getSocketChannel().read(connection.getPeerNetData());
                if (count == -1) {
                    handleEndOfStream(connection);
                    return;
                }
                doUnwrap(connection);
                break;

            case BUFFER_OVERFLOW:
                // The application input buffer does not have enough space, re-allocate and retry unwrap
                // (NOTE: application buffer size as reported by the SSL session might change dynamically)
                connection.setPeerData(ensureRemaining(connection.getPeerData(), connection.getEngine().getSession().getApplicationBufferSize()));
                doUnwrap(connection);
                break;
            default:
                throw new IllegalStateException("Invalid SSL status: " + status);
        }
    }

    private void handleEndOfStream(SSLConnection connection) throws IOException {
        try {
            // This will check if the server has sent the appropriate SSL close handshake alert and throws an exception
            // if it did not. Note that some servers don't, so this should not be treated as a fatal exception.
            connection.getEngine().closeInbound();
            close(connection);
        } catch (SSLException e) {
            // This exception might happen because some servers do not respond to the client's close notify alert
            // message during the SSL close handshake; they just close the connection. This is normally not a problem.
            log.debug("Exception while calling SSLEngine.closeInbound(): {}", e.getMessage());
            closeChannel(connection);
        }
    }

    private ByteBuffer ensureRemaining(ByteBuffer oldBuffer, int newRemaining) {
        if (oldBuffer.remaining() < newRemaining) {
            oldBuffer.flip();
            final ByteBuffer newBuffer = ByteBuffer.allocate(oldBuffer.remaining() + newRemaining);
            newBuffer.put(oldBuffer);
            return newBuffer;
        } else {
            // Buffer does not need to be reallocated, there is already enough remaining
            return oldBuffer;
        }
    }

    public void close(SSLConnection connection) throws IOException {
        // This tells the SSLEngine that we are not going to pass it any more application data
        // and prepares it for the close handshake
        log.trace("Performing closing SSL handshake");
        connection.getEngine().closeOutbound();

        // Perform close handshake
        checkHandshakeStatus(connection);

        closeChannel(connection);
    }

    private void closeChannel(SSLConnection connection) throws IOException {
        if (connection.getSocketChannel().isOpen()) {
            log.debug("Closing socket channel");
            connection.getSocketChannel().close();
        }
    }
}
