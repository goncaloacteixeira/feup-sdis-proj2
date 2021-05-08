package operations;

import messages.Message;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public abstract class Operation implements Runnable {
    protected final SocketChannel channel;
    protected final SSLEngine engine;

    public Operation(SocketChannel channel, SSLEngine engine) {
        this.channel = channel;
        this.engine = engine;
    }
}
