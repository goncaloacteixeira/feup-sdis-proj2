package peer;

import peer.chord.ChordReference;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteInterface extends Remote {
    String chord() throws RemoteException;

    ChordReference findSuccessor(int guid) throws RemoteException;
}
