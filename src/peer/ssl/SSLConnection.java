package peer.ssl;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public class SSLConnection {
    private final SocketChannel socketChannel;
    private final SSLEngine engine;
    private final boolean handshake;

    public SSLConnection(SocketChannel socketChannel, SSLEngine engine, boolean handshake) {
        this.socketChannel = socketChannel;
        this.engine = engine;
        this.handshake = handshake;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public SSLEngine getEngine() {
        return engine;
    }

    public boolean handshake() {
        return handshake;
    }
}
