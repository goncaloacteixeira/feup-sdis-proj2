package peer;

import messages.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class Peer extends ChordPeer {
    private final static Logger log = LogManager.getLogger(Peer.class);
    private String sap;

    public static void main(String[] args) throws UnknownHostException {
        if (args.length < 3) {
            System.out.println("Usage: java Peer <Service Access Point> <BOOT PEER IP> <BOOT PEER PORT> [-b]");
            System.out.println("Service Access Point: RMI bind");
            return;
        }

        String sap = args[0];
        InetAddress bootAddress = InetAddress.getByName(args[1]);
        int bootPort = Integer.parseInt(args[2]);
        boolean boot = (args.length > 3 && args[3].equals("-b"));

        try {
            Peer peer = new Peer(new InetSocketAddress(bootAddress, bootPort), boot, sap);
            new Thread(peer::start).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void start() {
        this.join();
        super.start();
    }

    public Peer(InetSocketAddress address, boolean boot, String sap) throws Exception {
        super(address, boot);
        this.sap = sap;
    }

    @Override
    public void read(SocketChannel socketChannel, SSLEngine engine) throws Exception {
        log.debug("[PEER] Reading data...");

        this.peerNetData.clear();
        int bytesRead = socketChannel.read(this.peerNetData);
        if (bytesRead > 0) {
            this.peerNetData.flip();
            while (this.peerNetData.hasRemaining()) {
                peerApplicationData.clear();
                SSLEngineResult result = engine.unwrap(this.peerNetData, this.peerApplicationData);
                switch (result.getStatus()) {
                    case OK:
                        this.peerApplicationData.flip();

                        byte[] buffer;
                        int size = peerApplicationData.remaining();
                        if (peerApplicationData.hasArray()) {
                            buffer = Arrays.copyOfRange(peerApplicationData.array(),
                                    peerApplicationData.arrayOffset() + peerApplicationData.position(),
                                    size);
                        } else {
                            buffer = new byte[peerApplicationData.remaining()];
                            peerApplicationData.duplicate().get(buffer);
                        }
                        Message message = Message.parse(buffer, size);
                        log.debug("Message received: " + message);
                        break;
                    case BUFFER_OVERFLOW:
                        this.peerApplicationData = this.enlargeApplicationBuffer(engine, this.peerApplicationData);
                        break;
                    case BUFFER_UNDERFLOW:
                        this.peerNetData = this.handleBufferUnderflow(engine, this.peerNetData);
                        break;
                    case CLOSED:
                        log.debug("The other peer requests closing the connection");
                        this.closeConnection(socketChannel, engine);
                        log.debug("Connection closed!");
                        break;
                    default:
                        throw new IllegalStateException("Invalid SSL Status: " + result.getStatus());
                }
            }
        } else if (bytesRead < 0) {
            log.error("Received EOS. Trying to close connection...");
            handleEndOfStream(socketChannel, engine);
            log.debug("Connection closed!");
        }
    }

    @Override
    public void write(SocketChannel socketChannel, SSLEngine engine, byte[] message) throws IOException {
        log.debug("[PEER] Writing data...");

        this.applicationData.clear();
        this.applicationData.put(message);
        this.applicationData.flip();
        while (this.applicationData.hasRemaining()) {
            this.netData.clear();
            SSLEngineResult result = engine.wrap(this.applicationData, this.netData);
            switch (result.getStatus()) {
                case OK:
                    this.netData.flip();
                    int bytesWritten = 0;
                    while (this.netData.hasRemaining()) {
                        bytesWritten += socketChannel.write(this.netData);
                    }
                    log.debug("Message Sent: " + Message.parse(message, message.length));
                    break;
                case BUFFER_OVERFLOW:
                    this.netData = enlargePacketBuffer(engine, this.netData);
                    break;
                case CLOSED:
                    this.closeConnection(socketChannel, engine);
                    return;
                default:
                    throw new IllegalStateException("Invalid SSL Status: " + result.getStatus());
            }
        }
    }
}
