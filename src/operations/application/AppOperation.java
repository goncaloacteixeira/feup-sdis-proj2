package operations.application;

import operations.Operation;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public abstract class AppOperation extends Operation {
    public AppOperation(SSLConnection connection) {
        super(connection);
    }
}
