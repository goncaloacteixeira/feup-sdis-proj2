package peer.backend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import peer.Constants;
import peer.Peer;
import peer.Utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PeerInternalState implements Serializable {
    private static final Logger log = LogManager.getLogger(PeerInternalState.class);

    private final ConcurrentHashMap<String, PeerFile> sentFilesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PeerFile> savedFilesMap = new ConcurrentHashMap<>();
    private transient ScheduledExecutorService scheduler;

    public static transient String PEER_DIR = "peer%d";
    public final static transient String FILES_PATH = "peer%d/%s";
    private static transient String DB_FILENAME = "peer%d/data.ser";

    private long capacity = Constants.DEFAULT_CAPACITY;
    private long occupation;

    private transient Peer peer;

    public PeerInternalState(Peer peer) {
        this.peer = peer;
    }

    private void init() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    private void startAsyncChecks() {
        this.scheduler.scheduleAtFixedRate(this::commit, 1, 5, TimeUnit.SECONDS);
    }

    public long getOccupation() {
        return occupation;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
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

        peerInternalState.init();
        peerInternalState.build();

        return peerInternalState;
    }

    private void build() {
        File directory = new File(PEER_DIR);
        // create dir if it does not exist
        if (!directory.exists())
            if (!directory.mkdir()) {
                log.info("Directory doesn't exist but could not be created");
                return;
            }
        try {
            new File(DB_FILENAME).createNewFile();
        } catch (IOException e) {
            log.info("[PIS] Could not load/create database file");
            e.printStackTrace();
            return;
        }

        log.info("Starting Async Tasks...");
        this.startAsyncChecks();

        // this.updateOccupation();
        log.info("Database Loaded/Created Successfully");
    }

    public void commit() {
        try {
            this.updateOccupation();
        } catch (IOException e) {
            log.error("Could not calculate occupation: {}", e.getMessage());
        }

        try {
            FileOutputStream fileOut = new FileOutputStream(DB_FILENAME);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(this);
            out.flush();
            out.close();
            fileOut.close();
        } catch (IOException i) {
            log.error("Could not commit database: {}", i.getMessage());
        }
    }

    public boolean hasSpace(double size) {
        return size < (this.capacity - this.occupation);
    }

    public void updateOccupation() throws IOException {
        occupation = Files.walk(Path.of(PEER_DIR))
                .filter(p -> p.toFile().isFile())
                .mapToLong(p -> p.toFile().length())
                .sum();
    }

    public void addSentFile(String filename, PeerFile file) {
        if (this.sentFilesMap.containsKey(filename)) {
            for (Integer key : this.sentFilesMap.get(filename).getKeys()) {
                file.addKey(key);
            }
        }
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
        ret.append(String.format("Occupation: %s\n", Utils.prettySize(this.occupation)));
        ret.append("-------------- END OF REPORT --------------").append("\n");

        return ret.toString();

    }
}
