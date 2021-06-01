package operations;

import peer.ssl.SSLConnection;

/**
 * Operation Abstract Class
 */
public abstract class Operation implements Runnable {
    protected final SSLConnection connection;

    public Operation(SSLConnection connection) {
        this.connection = connection;
    }
}
