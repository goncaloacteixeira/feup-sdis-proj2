package peer;

/**
 * Constants used on this Peer
 */
public class Constants {
    public final static int CHUNK_SIZE = 10240;
    public final static int TLS_CHUNK_SIZE = 10269;
    public final static long DEFAULT_CAPACITY = (long) Math.pow(2, 31); // 2.15GB
    public final static int M_BIT = 8;
    public final static int CHORD_MAX_PEERS = (int) Math.pow(2, M_BIT);
}
