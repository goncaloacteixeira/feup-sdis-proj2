package peer.backend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Constants;
import peer.Peer;
import peer.Utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PeerInternalState implements Serializable {
    private static final Logger log = LogManager.getLogger(PeerInternalState.class);

    private final ConcurrentHashMap<String, PeerFile> sentFilesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PeerFile> savedFilesMap = new ConcurrentHashMap<>();

    public static transient String PEER_DIR = "peer%d";
    public final static transient String FILES_PATH = "peer%d/%s";
    private static transient String DB_FILENAME = "peer%d/data.ser";

    private long capacity = Constants.DEFAULT_CAPACITY;
    private long ocupation;

    private transient Peer peer;

    public PeerInternalState(Peer peer) {
        this.peer = peer;
    }

    public static PeerInternalState load(Peer peer) {
        PEER_DIR = String.format(PEER_DIR, peer.getGuid());
        DB_FILENAME = String.format(DB_FILENAME, peer.getGuid());

        PeerInternalState peerInternalState = null;

        try {
            FileInputStream inputStream = new FileInputStream(DB_FILENAME);
            ObjectInputStream objectIn = new ObjectInputStream(inputStream);
            peerInternalState = (PeerInternalState) objectIn.readObject();
            peerInternalState.peer = peer;
            inputStream.close();
            objectIn.close();
        } catch (IOException | ClassNotFoundException e) {
            log.info("Couldn't Load Database. Creating one now...");
        }

        if (peerInternalState == null) {
            peerInternalState = new PeerInternalState(peer);
        }

        peerInternalState.build();

        return peerInternalState;
    }

    private void build() {
        File directory = new File(PEER_DIR);
        // create dir if it does not exist
        if (!directory.exists())
            if (!directory.mkdir()) {
                System.out.println("[PIS] Directory doesn't exist but could not be created");
                return;
            }
        try {
            new File(DB_FILENAME).createNewFile();
        } catch (IOException e) {
            System.out.println("[PIS] Could not load/create database file");
            e.printStackTrace();
            return;
        }
        // this.updateOccupation();
        System.out.println("[PIS] Database Loaded/Created Successfully");
    }

    public void commit() {
        try {
            FileOutputStream fileOut = new FileOutputStream(DB_FILENAME);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this);
            out.flush();
            out.close();
            fileOut.close();
        } catch (IOException i) {
            i.printStackTrace();
        }
        // this.updateOccupation();
    }

    public void addSentFile(String filename, PeerFile file) {
        this.sentFilesMap.put(filename, file);
    }

    public void addSavedFile(PeerFile file) {
        this.savedFilesMap.put(file.getId(), file);
    }

    public ConcurrentHashMap<String, PeerFile> getSentFilesMap() {
        return sentFilesMap;
    }

    public ConcurrentHashMap<String, PeerFile> getSavedFilesMap() {
        return savedFilesMap;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append(String.format("-------------- PEER %d REPORT --------------\n", peer.getGuid()));
        ret.append("-- Backup Files --\n");
        for (Map.Entry<String, PeerFile> entry : this.sentFilesMap.entrySet()) {
            PeerFile file = entry.getValue();
            ret.append(file).append("\n");
        }
        ret.append("-- Saved Files --\n");
        for (Map.Entry<String, PeerFile> savedChunkEntry : this.savedFilesMap.entrySet()) {
            PeerFile file = savedChunkEntry.getValue();
            ret.append(file).append("\n");
        }
        ret.append("----- Storage -----").append("\n");
        ret.append(String.format("Capacity: %s\n", Utils.prettySize(this.capacity)));
        // ret.append(String.format("Occupation: %.2fKB\n", this.occupation / 1000.0));
        ret.append("-------------- END OF REPORT --------------").append("\n");

        return ret.toString();

    }
}
