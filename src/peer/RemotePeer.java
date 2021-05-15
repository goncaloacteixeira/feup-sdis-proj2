package peer;

import peer.chord.ChordReference;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemotePeer extends Remote {
    String chord() throws RemoteException;

    ChordReference findSuccessor(int guid) throws RemoteException;

    String backup(String filename, int replicationDegree) throws RemoteException;

    String restore(String filename) throws RemoteException;

    String state() throws RemoteException;
}
