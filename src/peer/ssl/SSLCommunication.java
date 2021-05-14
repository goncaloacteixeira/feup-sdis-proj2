package peer.ssl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Constants;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SSLCommunication<M> {
    private final Logger log = LogManager.getLogger(SSLCommunication.class);
    private final Decoder<M> decoder;
    private final Encoder<M> encoder;
    private final Sizer<M> sizer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SSLCommunication(Decoder<M> decoder, Encoder<M> encoder, Sizer<M> sizer) {
        this.decoder = decoder;
        this.encoder = encoder;
        this.sizer = sizer;
    }

    M receive(SSLConnection connection) throws Exception {
        log.debug("Reading data...");

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
                        this.closeConnection(connection);
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
        log.debug("Writing message...");

        SSLEngine engine = connection.getEngine();

        connection.setAppData(ByteBuffer.allocate(sizer.size(message)));
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

    public void closeConnection(SSLConnection connection) throws IOException {
        log.debug("Closing connection...");
        connection.getEngine().closeOutbound();
        doHandshake(connection);
        connection.getSocketChannel().close();
        log.debug("Connection closed successfully!");
    }

    protected boolean doHandshake(SSLConnection connection) throws IOException {
        log.debug("Starting handshake: {}", connection.getSocketChannel());

        SSLEngineResult result;
        SSLEngineResult.HandshakeStatus handshakeStatus;

        SSLEngine engine = connection.getEngine();
        SocketChannel socketChannel = connection.getSocketChannel();

        int appBufferSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer netData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer appData = ByteBuffer.allocate(appBufferSize);
        connection.getNetData().clear();
        connection.getPeerNetData().clear();

        handshakeStatus = engine.getHandshakeStatus();
        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

            log.trace("HSS: {}", handshakeStatus);

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
                        result = engine.unwrap(connection.getPeerNetData(), connection.getPeerData());
                        /*do {
                            result = engine.unwrap(connection.getPeerNetData(), connection.getPeerData());
                        } while (connection.getPeerNetData().hasRemaining() || result.bytesProduced() > 0);*/
                        log.trace("After unwrap: {}", result);
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
                            connection.setPeerData(enlargeApplicationBuffer(engine, connection.getPeerData()));
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
                    netData.clear();
                    try {
                        result = engine.wrap(appData, netData);
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException e) {
                        log.error("Error processing data, will try to close gracefully: " + e + " localized: " + e.getLocalizedMessage() + " cause: " + e.getCause());
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
                        executor.submit(task);
                    }
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL Status: " + handshakeStatus);
            }
        }
        log.debug("Handshake Valid!");
        return true;
    }

    protected void sendFile(SSLConnection connection, FileChannel fileChannel) throws IOException, InterruptedException {
        SSLEngine engine = connection.getEngine();

        connection.setAppData(ByteBuffer.allocate(Constants.CHUNK_SIZE));
        int bytesRead = fileChannel.read(connection.getAppData());

        while (bytesRead != -1) {
            log.debug("Bytes read from file: {}", bytesRead);
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
                        log.debug("Bytes written to socket: {}", bytesWritten);
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
            connection.getAppData().clear();
            bytesRead = fileChannel.read(connection.getAppData());
        }
    }

    protected int receiveFile(SSLConnection connection, FileChannel fileChannel) throws IOException {
        SSLEngine engine = connection.getEngine();

        connection.getPeerNetData().clear();
        connection.getSocketChannel().socket().setSoTimeout(1000);
        ReadableByteChannel byteChannel = Channels.newChannel(connection.getSocketChannel().socket().getInputStream());

        int bytesRead = 0;
        do {
            int read;
            try {
                read = byteChannel.read(connection.getPeerNetData());
            } catch (Exception e) {
                log.debug("Reached last chunk");
                break;
            }
            log.debug("Read in single read: {}", read);
            bytesRead += read;
        } while (bytesRead % Constants.TLS_CHUNK_SIZE != 0);

        connection.getPeerNetData().flip();

        log.debug("Bytes read from socket: {}", bytesRead);

        int bytesConsumed = 0;

        ByteBuffer aux;

        if (bytesRead > 0) {
            int bytesWritten = 0;
            while (bytesConsumed != bytesRead) {
                connection.getPeerData().clear();
                byte[] sub = new byte[Math.min(Constants.TLS_CHUNK_SIZE, bytesRead - bytesConsumed)];
                connection.getPeerNetData().get(sub, 0, Math.min(Constants.TLS_CHUNK_SIZE, bytesRead - bytesConsumed));
                aux = ByteBuffer.wrap(sub);

                SSLEngineResult result = engine.unwrap(aux, connection.getPeerData());
                bytesConsumed += result.bytesConsumed();

                switch (result.getStatus()) {
                    case OK:
                        connection.getPeerData().flip();
                        bytesWritten += fileChannel.write(connection.getPeerData());
                        break;
                    case BUFFER_OVERFLOW:
                        connection.setPeerData(this.enlargeApplicationBuffer(engine, connection.getPeerData()));
                        break;
                    case BUFFER_UNDERFLOW:
                        connection.setPeerNetData(this.handleBufferUnderflow(engine, connection.getPeerNetData()));
                        break;
                    case CLOSED:
                        log.debug("The other peer requests closing the connection");
                        this.closeConnection(connection);
                        log.debug("Connection closed!");
                        return -1;
                    default:
                        throw new IllegalStateException("Invalid SSL Status: " + result.getStatus());
                }
            }
            log.debug("Wrote packet: {}", bytesWritten);
            return bytesWritten;
        } else if (bytesRead < 0) {
            log.error("Received EOS. Trying to close connection...");
            handleEndOfStream(connection);
            log.debug("Connection closed!");
        }
        return bytesRead;
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

    protected static KeyManager[] createKeyManager(String filepath, String keystorePassword, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(filepath)) {
            keyStore.load(is, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());
        return kmf.getKeyManagers();
    }

    protected static TrustManager[] createTrustManager(String filepath, String keystorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(filepath)) {
            trustStore.load(is, keystorePassword.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }
}
