package messages.chord;

import peer.Constants;
import peer.chord.ChordReference;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Guid extends ChordMessage {
    private final int guid;
    private final ChordReference[] fingerTable = new ChordReference[Constants.M_BIT];

    public Guid(ChordReference sender, byte[] body) {
        super("CHORD", "GUID", sender, body);

        String[] parts = new String(body).split("\n::\n");
        this.guid = Integer.parseInt(parts[0]);

        if (parts.length == 1) return;

        String[] fingerTableEntries = parts[1].split("\n");
        for (String part : fingerTableEntries) {
            Pattern p = Pattern.compile("^(\\d)+-(\\d)+\\((.*?):(.*?)\\)$");
            Matcher m = p.matcher(part);
            if (m.matches()) {
                try {
                    int index = Integer.parseInt(m.group(1));
                    int guid = Integer.parseInt(m.group(2));
                    InetAddress address = InetAddress.getByName(m.group(3));
                    int port = Integer.parseInt(m.group(4));
                    this.fingerTable[index] = new ChordReference(new InetSocketAddress(address, port), guid);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public ChordReference[] getFingerTable() {
        return fingerTable;
    }

    public int getGuid() {
        return guid;
    }

    @Override
    public String toString() {
        return "Guid{" +
                "type='" + type + '\'' +
                ", operation='" + operation + '\'' +
                ", sender=" + sender +
                ", guid=" + guid +
                '}';
    }
}
