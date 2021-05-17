package operations.application;

import messages.application.ApplicationMessage;
import messages.chord.*;
import operations.Operation;
import operations.chord.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Peer;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public abstract class AppOperation extends Operation {
    protected final Logger log = LogManager.getLogger(getClass());

    protected final ApplicationMessage message;
    protected final Peer context;

    public AppOperation(SSLConnection connection, ApplicationMessage message, Peer context) {
        super(connection);
        this.message = message;
        this.context = context;
    }

    public static AppOperation parse(ApplicationMessage message, Peer context, SSLConnection connection) {
        switch (message.getOperation()) {
            case "BACKUP":
                return new BackupOp(connection, message, context);
            case "GET":
                return new GetOp(connection, message, context);
            case "DELETE":
                return new DeleteOp(connection, message, context);
            case "REMOVED":
                return new RemovedOp(connection, message, context);
            default:
                return null;
        }
    }
}
