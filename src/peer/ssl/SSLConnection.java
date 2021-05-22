package peer.ssl;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Class Responsible for handling a connection, this class contains the associated socket channel, the engine used
 * and the four byte buffers for the connection.
 */
public class SSLConnection {
    private final SocketChannel socketChannel;
    private final SSLEngine engine;
    private boolean handshake = false;
    private ByteBuffer appData;
    private ByteBuffer netData;
    private ByteBuffer peerData;
    private ByteBuffer peerNetData;

    public SSLConnection(SocketChannel socketChannel, SSLEngine engine, boolean handshake, ByteBuffer appData, ByteBuffer netData, ByteBuffer peerData, ByteBuffer peerNetData) {
        this.socketChannel = socketChannel;
        this.engine = engine;
        this.handshake = handshake;
        this.appData = appData;
        this.netData = netData;
        this.peerData = peerData;
        this.peerNetData = peerNetData;
    }

    public SSLConnection(SocketChannel socketChannel, SSLEngine engine, ByteBuffer appData, ByteBuffer netData, ByteBuffer peerData, ByteBuffer peerNetData) {
        this.socketChannel = socketChannel;
        this.engine = engine;
        this.appData = appData;
        this.netData = netData;
        this.peerData = peerData;
        this.peerNetData = peerNetData;
    }

    public SSLConnection(SocketChannel socketChannel, SSLEngine engine) {
        this.socketChannel = socketChannel;
        this.engine = engine;
    }

    public void setHandshake(boolean handshake) {
        this.handshake = handshake;
    }

    public void setAppData(ByteBuffer appData) {
        this.appData = appData;
    }

    public void setNetData(ByteBuffer netData) {
        this.netData = netData;
    }

    public void setPeerData(ByteBuffer peerData) {
        this.peerData = peerData;
    }

    public void setPeerNetData(ByteBuffer peerNetData) {
        this.peerNetData = peerNetData;
    }

    public ByteBuffer getAppData() {
        return appData;
    }

    public ByteBuffer getNetData() {
        return netData;
    }

    public ByteBuffer getPeerData() {
        return peerData;
    }

    public ByteBuffer getPeerNetData() {
        return peerNetData;
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
