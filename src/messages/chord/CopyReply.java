package messages.chord;

import peer.chord.ChordReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CopyReply extends ChordMessage {
    private final List<Map.Entry<Integer, String>> files = new ArrayList<>();

    public CopyReply(ChordReference sender, byte[] body) {
        super("CHORD", "COPYREPLY", sender, body);

        if (new String(body).equals("NONE")) {
            return;
        }

        String[] files = new String(body).split("::");

        for (String file : files) {
            String[] parts = file.split(":");

            this.files.add(Map.entry(Integer.parseInt(parts[0]), parts[1]));
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

    public List<Map.Entry<Integer, String>> getFiles() {
        return files;
    }
}
