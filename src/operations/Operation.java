package operations;

import messages.Message;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public abstract class Operation implements Runnable {
    protected final SSLConnection connection;

    public Operation(SSLConnection connection) {
        this.connection = connection;
    }
}
