package messages;

import messages.chord.ChordMessage;
import operations.Operation;
import peer.Peer;
import peer.chord.ChordReference;
import peer.ssl.SSLConnection;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public abstract class Message {
    protected final String type;
    protected final String operation;
    protected final ChordReference sender;
    protected byte[] body;

    public Message(String type, String operation, ChordReference sender) {
        this.type = type;
        this.operation = operation;
        this.sender = sender;
        this.body = new byte[0];
    }

    public Message(String type, String operation, ChordReference sender, byte[] body) {
        this.type = type;
        this.operation = operation;
        this.sender = sender;
        this.body = body;
    }

    public abstract byte[] encode();

    public abstract Operation getOperation(Peer context, SSLConnection connection);

    public static Message parse(byte[] buffer, int size) {
        String packetData = new String(buffer);
        packetData = packetData.substring(0, Math.min(size, packetData.length()));
        // Splitting the packet data into two parts -> header and body, the splitter is two CRLF i.e. 2 \r\n
        String[] parts = packetData.split("\r\n\r\n", 2);

        int headerBytes = parts[0].length();
        // as the header may have two or more spaces between the arguments, then this replace statement cleans the header
        // we also want to trim the result so no trailing or leading spaces are present
        parts[0] = parts[0].replaceAll("^ +| +$|( )+", "$1").trim();

        // the arguments are split by a space
        String[] args = parts[0].split(" ");

        // saving the data
        String type = args[0];
        ChordReference sender = ChordReference.parse(args[1]);

        byte[] body = new byte[0];

        if (parts.length == 2) body = Arrays.copyOfRange(buffer, headerBytes + 4, size);

        if (type.equals("CHORD")) {
            // parse chord message
            return ChordMessage.parse(sender, parts[0].split("\r\n")[1], body);
        } else if (type.equals("APP")) {
            // parse application message

        }
        return null;
    }

    public String getType() {
        return type;
    }

    public String getOperation() {
        return operation;
    }

    public ChordReference getSender() {
        return sender;
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                '}';
    }
}
