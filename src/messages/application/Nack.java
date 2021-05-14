package messages.application;

import peer.chord.ChordReference;

public class Nack extends ApplicationMessage {
    private final String message;

    public Nack(ChordReference sender, byte[] body) {
        super("NACK", sender, body);

        message = new String(body);
    }

    @Override
    public String toString() {
        return "Nack{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", message='" + message + '\'' +
                '}';
    }

    public String getMessage() {
        return message;
    }
}
