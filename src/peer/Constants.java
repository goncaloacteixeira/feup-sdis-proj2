package peer;

/**
 * Constants used on this Peer
 */
public class Constants {
    public final static int CHUNK_SIZE = 64000;
    public final static long DEFAULT_CAPACITY = 100000000; // 100MB
    public final static int REQUESTS_WORKERS = 16;
    public final static int ACKS_WORKERS = 128;
    public final static int TRIAGE_WORKERS = 64;
    public final static int IO_WORKERS = 16;
    public final static int M_BIT = 3;
    public final static int CHORD_MAX_PEERS = (int) Math.pow(2, M_BIT);
}
