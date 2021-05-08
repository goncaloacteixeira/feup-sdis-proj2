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
        int key = 2;
        System.out.println(ChordPeer.between(key, 1, 2, true));
        System.out.println(ChordPeer.between(key, 0, 1, true));
        System.out.println(ChordPeer.between(key, 0, 2, false));
        System.out.println(ChordPeer.between(key, 2, 0, false));
        System.out.println(ChordPeer.between(key, 1, 0, false));
    }
}
