package peer.ssl;

import java.nio.ByteBuffer;

public interface Decoder<M> {
    M decode(ByteBuffer buffer);
}
