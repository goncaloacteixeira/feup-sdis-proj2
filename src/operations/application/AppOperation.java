package operations.application;

import operations.Operation;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public abstract class AppOperation extends Operation {
    public AppOperation(SocketChannel channel, SSLEngine engine) {
        super(channel, engine);
    }
}
