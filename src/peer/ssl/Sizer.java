package peer.ssl;

public interface Sizer<M> {
    int size(M message);
}
