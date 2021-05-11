package peer.ssl;

import messages.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SSLPeer {
    public static final Logger log = LogManager.getLogger(SSLPeer.class);

    protected InetSocketAddress address;
    private final SSLContext context;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final SSLServer<Message> server;
    private final SSLClient<Message> client;

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

    private static int sizer(Message message) {
        return message.encode().length;
    }

    public SSLPeer(InetSocketAddress bootAddress, boolean boot) throws Exception {
        if (boot) {
            this.address = bootAddress;
        } else {
            this.address = new InetSocketAddress(InetAddress.getLocalHost(), 0);
        }

        this.context = SSLContext.getInstance("TLSv1.2");
        context.init(
                SSLCommunication.createKeyManager("resources/server.jks", "sdisg27", "sdisg27"),
                SSLCommunication.createTrustManager("resources/truststore.jks", "sdisg27"),
                new SecureRandom());

        this.server = new SSLServer<>(context, this.address, SSLPeer::decode, SSLPeer::encode, SSLPeer::sizer);
        this.client = new SSLClient<>(context, SSLPeer::decode, SSLPeer::encode, SSLPeer::sizer);

        this.address = this.server.getAddress();
        this.server.addObserver(this);
        new Thread(this.server::start).start();
    }

    public synchronized SSLConnection connectToPeer(InetSocketAddress address) {
        try {
            log.debug("Connecting to peer: " + address);
            return this.client.connectToPeer(address);
        } catch (IOException e) {
            log.trace("Could not connect to peer {}, exception: {}", address, e.getMessage());
        }
        return null;
    }

    public boolean send(SSLConnection connection, Message message) {
        try {
            this.client.send(connection, message);
            return true;
        } catch (IOException e) {
            log.trace("Could not send message to peer, exception: {}", e.getMessage());
        }
        return false;
    }

    public Message receive(SSLConnection connection) {
        try {
            return this.client.receive(connection);
        } catch (Exception e) {
            log.trace("Could not receive message from peer, exception: {}", e.getMessage());
        }
        return null;
    }

    public Message receiveBlocking(SSLConnection connection, int timeToRead) throws MessageTimeoutException {
        Message reply;
        int attempt = 0;
        while ((reply = this.receive(connection)) == null && attempt < 50) {
            attempt++;
            try {
                Thread.sleep(timeToRead * 3L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (reply == null) {
            throw new MessageTimeoutException("Message took too long to receive!");
        }

        return reply;
    }

    public void handleNotification(Object message, SSLConnection connection) {
        // ((Message) message).getOperation((Peer) this, connection).run();
        executor.submit(((Message) message).getOperation((Peer) this, connection));
    }

    public boolean closeConnection(SSLConnection connection) {
        try {
            this.client.closeConnection(connection);
            return true;
        } catch (IOException e) {
            log.trace("Could not close connection to peer, exception: {}", e.getMessage());
        }
        return false;
    }
}
