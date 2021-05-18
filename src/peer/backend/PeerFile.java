package peer.backend;

import peer.chord.ChordReference;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeerFile implements Serializable {
    private final int key;
    private final String id;
    private final ChordReference owner;
    private final long size;
    private final int replicationDegree;
    private final Set<Integer> keys = new HashSet<>();
    public boolean beingDeleted = false;

    public PeerFile(int key, String id, ChordReference owner, long size, int replicationDegree) {
        // key -1 means it belongs to this peer
        this.key = key;
        this.id = id;
        this.owner = owner;
        this.size = size;
        this.replicationDegree = replicationDegree;
    }

    public void addKeys(List<Integer> keys) {
        this.keys.addAll(keys);
    }

    public void addKey(int key) {
        this.keys.add(key);
    }

    public int getKey() {
        return key;
    }

    public String getId() {
        return id;
    }

    public ChordReference getOwner() {
        return owner;
    }

    public long getSize() {
        return size;
    }

    public int getReplicationDegree() {
        return replicationDegree;
    }

    @Override
    public String toString() {
        return "PeerFile{" +
                "key=" + key +
                ", id='" + id + '\'' +
                ", owner=" + owner +
                ", size=" + size +
                ", replicationDegree=" + replicationDegree +
                ", keys=" + keys +
                '}';
    }

    public Set<Integer> getKeys() {
        return keys;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerFile peerFile = (PeerFile) o;
        return Objects.equals(id, peerFile.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
