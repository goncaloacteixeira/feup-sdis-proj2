package test;

import messages.Message;
import messages.chord.Join;
import peer.chord.ChordPeer;
import peer.chord.ChordReference;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Test {
    public static void main(String[] args) throws UnknownHostException {
        ChordReference reference = new ChordReference(new InetSocketAddress(InetAddress.getLocalHost(), 0), 0);
        ChordReference reference1 = new ChordReference(new InetSocketAddress(InetAddress.getByName("192.12.1.1"), 0), 0);
        ChordReference reference2 = new ChordReference(new InetSocketAddress(InetAddress.getByName("192.121.1.1"), 0), 0);
        ChordReference reference3 = new ChordReference(new InetSocketAddress(InetAddress.getByName("192.115.1.1"), 0), 0);
        ChordReference reference4 = new ChordReference(new InetSocketAddress(InetAddress.getLocalHost(), 1), 0);

        System.out.println(ChordPeer.generateNewKey(reference.getAddress()));
        System.out.println(ChordPeer.generateNewKey(reference1.getAddress()));
        System.out.println(ChordPeer.generateNewKey(reference2.getAddress()));
        System.out.println(ChordPeer.generateNewKey(reference3.getAddress()));
        System.out.println(ChordPeer.generateNewKey(reference4.getAddress()));
    }
}
