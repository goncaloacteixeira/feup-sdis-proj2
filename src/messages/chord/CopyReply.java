package messages.chord;

import peer.backend.PeerFile;
import peer.chord.ChordReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CopyReply extends ChordMessage {
    private final List<PeerFile> files = new ArrayList<>();

    public CopyReply(ChordReference sender, byte[] body) {
        super("CHORD", "COPYREPLY", sender, body);

        if (new String(body).equals("NONE")) {
            return;
        }

        String[] files = new String(body).split("::");

        for (String file : files) {
            String[] parts = file.split("\\|");

            this.files.add(new PeerFile(Integer.parseInt(parts[0]), parts[1], ChordReference.parse(parts[2]), Long.parseLong(parts[3]), Integer.parseInt(parts[4])));
        }
    }

    @Override
    public String toString() {
        return "CopyReply{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", files=" + files +
                '}';
    }

    public List<PeerFile> getFiles() {
        return files;
    }
}
