package peer.ssl;

import java.nio.ByteBuffer;

public interface Encoder<M> {
    void encode(M value, ByteBuffer buffer);
}
