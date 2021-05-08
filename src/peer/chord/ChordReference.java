package peer.chord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChordReference {
    private static final Logger log = LogManager.getLogger(ChordReference.class);
    private InetSocketAddress address;
    private int guid;

    public ChordReference(InetSocketAddress address, int guid) {
        this.address = address;
        this.guid = guid;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public int getGuid() {
        return guid;
    }

    public static ChordReference parse(String ref) {
        Pattern p = Pattern.compile("^(-?\\d+)\\((.*?):(.*?)\\)$");
        Matcher m = p.matcher(ref);
        if (m.matches()) {
            try {
                int guid = Integer.parseInt(m.group(1));
                InetAddress address = InetAddress.getByName(m.group(2));
                int port = Integer.parseInt(m.group(3));
                return new ChordReference(new InetSocketAddress(address, port), guid);
            } catch (UnknownHostException e) {
                log.debug("Error parsing chord reference: " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%d(%s:%d)", this.guid, this.address.getAddress().getHostAddress(), this.address.getPort());
    }

    public void setGuid(int guid) {
        this.guid = guid;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }
}
