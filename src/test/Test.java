package test;

import messages.Message;
import messages.chord.Join;
import peer.chord.ChordReference;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Test {
    public static void main(String[] args) throws UnknownHostException {
        ChordReference reference = new ChordReference(new InetSocketAddress(InetAddress.getLocalHost(), 0), 0);

        Message message = new Join(reference);

        System.out.println(message);
        System.out.println(new String(message.encode(), StandardCharsets.UTF_8));
        System.out.println(Message.parse(message.encode(), message.encode().length));
    }
}
