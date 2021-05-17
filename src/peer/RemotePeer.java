package peer;

import client.ClientCallbackInterface;
import peer.chord.ChordReference;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemotePeer extends Remote {
    void chord() throws RemoteException;

    void clientFindSuccessor(int guid) throws RemoteException;

    void backup(String filename, int replicationDegree) throws RemoteException;

    void restore(String filename) throws RemoteException;

    void delete(String filename) throws RemoteException;

    void register(ClientCallbackInterface callbackInterface) throws RemoteException;

    void state() throws RemoteException;
}
