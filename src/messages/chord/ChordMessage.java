package messages.chord;

import messages.Message;
import operations.Operation;
import operations.chord.ChordOperation;
import peer.Peer;
import peer.chord.ChordReference;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public abstract class ChordMessage extends Message {

    public ChordMessage(String type, String operation, ChordReference sender) {
        super(type, operation, sender);
    }

    public ChordMessage(String type, String operation, ChordReference sender, byte[] body) {
        super(type, operation, sender, body);
    }

    public static ChordMessage parse(ChordReference sender, String header, byte[] body) {
        header = header.replaceAll("^ +| +$|( )+", "$1").trim();
        String[] args = header.split(" ");
        String chordType = args[0];

        switch (chordType) {
            case "JOIN":
                return new Join(sender);
            case "GUID":
                return new Guid(sender, body);
            default:
                return null;
        }
    }

    @Override
    public Operation getOperation(Peer context, SocketChannel channel, SSLEngine engine) {
        return ChordOperation.parse(this, context, channel, engine);
    }

    @Override
    public byte[] encode() {
        byte[] header = String.format("%s %s \r\n %s \r\n\r\n",
                "CHORD", this.sender, this.operation).getBytes(StandardCharsets.UTF_8);

        byte[] toSend = new byte[header.length + this.body.length];
        System.arraycopy(header, 0, toSend, 0, header.length);
        System.arraycopy(this.body, 0, toSend, header.length, body.length);
        return toSend;
    }
}
