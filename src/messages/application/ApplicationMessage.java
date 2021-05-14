package messages.application;

import messages.Message;
import messages.chord.*;
import operations.Operation;
import operations.application.AppOperation;
import operations.chord.ChordOperation;
import peer.Peer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import java.nio.charset.StandardCharsets;

public abstract class ApplicationMessage extends Message {
    public ApplicationMessage(String operation, ChordReference sender) {
        super("APP", operation, sender);
    }

    public ApplicationMessage(String operation, ChordReference sender, byte[] body) {
        super("APP", operation, sender, body);
    }

    public static ApplicationMessage parse(ChordReference sender, String header, byte[] body) {
        header = header.replaceAll("^ +| +$|( )+", "$1").trim();
        String[] args = header.split(" ");
        String chordType = args[0];

        switch (chordType) {
            case "ACK":
                return new Ack(sender);
            case "NACK":
                return new Nack(sender, body);
            case "BACKUP":
                return new Backup(sender, body);
            default:
                return null;
        }
    }

    @Override
    public byte[] encode() {
        byte[] header = String.format("%s %s \r\n %s \r\n\r\n",
                "APP", this.sender, this.operation).getBytes(StandardCharsets.UTF_8);

        byte[] toSend = new byte[header.length + this.body.length];
        System.arraycopy(header, 0, toSend, 0, header.length);
        System.arraycopy(this.body, 0, toSend, header.length, body.length);
        return toSend;
    }

    @Override
    public Operation getOperation(Peer context, SSLConnection connection) {
        return AppOperation.parse(this, context, connection);
    }
}
