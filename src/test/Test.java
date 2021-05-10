package test;

import messages.Message;
import messages.chord.Guid;
import messages.chord.Join;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;
import peer.ssl.SSLPeer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Test extends SSLPeer {
    private final Logger log = LogManager.getLogger(String.valueOf(this.address.getPort()));

    public Test(InetSocketAddress bootAddress, boolean boot) throws Exception {
        super(bootAddress, boot);
    }

    public static void main(String[] args) throws Exception {
        SSLPeer peer1 = new Test(new InetSocketAddress(InetAddress.getLocalHost(), 8001), true);
        SSLPeer peer2 = new Test(new InetSocketAddress(InetAddress.getLocalHost(), 8002), true);

        new Thread(peer1::start).start();
        new Thread(peer2::start).start();

        SSLConnection connection = peer1.connectToPeer(peer2.getAddress(), false);
        peer1.sendMessage(connection, new Join(new ChordReference(peer1.getAddress(), 1)));
        peer2.write(connection, new Guid(new ChordReference(peer2.getAddress(), 2), "1".getBytes(StandardCharsets.UTF_8)).encode());
        peer1.read(connection);
        peer1.closeConnection(connection);

        connection = peer1.connectToPeer(peer2.getAddress(), false);
        peer1.sendMessage(connection, new Join(new ChordReference(peer1.getAddress(), 1)));
        peer2.write(connection, new Guid(new ChordReference(peer2.getAddress(), 2), "1".getBytes(StandardCharsets.UTF_8)).encode());
        peer1.read(connection);
        peer1.closeConnection(connection);

        connection = peer2.connectToPeer(peer1.getAddress(), false);
        peer2.sendMessage(connection, new Join(new ChordReference(peer2.getAddress(), 2)));
        peer1.write(connection, new Guid(new ChordReference(peer1.getAddress(), 1), "2".getBytes(StandardCharsets.UTF_8)).encode());
        peer2.read(connection);
        peer2.closeConnection(connection);
    }

    public void read(SSLConnection connection) throws Exception {
        readWithReply(connection);
    }

    public Object readWithReply(SSLConnection connection) throws Exception {
        log.debug("[PEER] Reading data...");

        SSLEngine engine = connection.getEngine();

        connection.getPeerNetData().clear();

        while (true) {
            int bytesRead = connection.getSocketChannel().read(connection.getPeerNetData());
            if (bytesRead > 0) {
                connection.getPeerNetData().flip();
                while (connection.getPeerNetData().hasRemaining()) {
                    connection.getPeerData().clear();
                    SSLEngineResult result = engine.unwrap(connection.getPeerNetData(), connection.getPeerData());

                    Thread.sleep(100);
                    log.debug("READ: " + result.getStatus());

                    switch (result.getStatus()) {
                        case OK:
                            connection.getPeerData().flip();

                            byte[] buffer;
                            int size = connection.getPeerData().remaining();
                            if (connection.getPeerData().hasArray()) {
                                buffer = Arrays.copyOfRange(connection.getPeerData().array(),
                                        connection.getPeerData().arrayOffset() + connection.getPeerData().position(),
                                        size);
                            } else {
                                buffer = new byte[connection.getPeerData().remaining()];
                                connection.getPeerData().duplicate().get(buffer);
                            }
                            Message message = Message.parse(buffer, size);
                            log.debug("Message received: " + message);
                            return message;
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
            } else if (bytesRead < 0) {
                log.error("Received EOS. Trying to close connection...");
                handleEndOfStream(connection);
                log.debug("Connection closed!");
            }
        }
    }

    public void write(SSLConnection connection, byte[] message) throws IOException {
        log.debug("[PEER] Writing data...");

        SSLEngine engine = connection.getEngine();

        connection.getAppData().clear();
        connection.getAppData().put(message);
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
                    log.debug("Message Sent: " + Message.parse(message, message.length));
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

}
