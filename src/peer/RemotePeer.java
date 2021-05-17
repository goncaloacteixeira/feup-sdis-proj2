package peer;

import client.ClientCallbackInterface;
import peer.chord.ChordReference;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemotePeer extends Remote {
    String chord() throws RemoteException;

    ChordReference findSuccessor(int guid) throws RemoteException;

    void backup(String filename, int replicationDegree) throws RemoteException;

    String restore(String filename) throws RemoteException;

    String delete(String filename) throws RemoteException;

    void register(ClientCallbackInterface callbackInterface) throws RemoteException;

    void state() throws RemoteException;
}
