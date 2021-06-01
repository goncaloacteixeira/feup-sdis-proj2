package peer;

import client.ClientCallbackInterface;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote Peer Interface Stub used for the RMI communication
 */
public interface RemotePeer extends Remote {
    void chord() throws RemoteException;

    void clientFindSuccessor(int guid) throws RemoteException;

    void backup(String filename, int replicationDegree) throws RemoteException;

    void restore(String filename) throws RemoteException;

    void delete(String filename) throws RemoteException;

    void reclaim(long size) throws RemoteException;

    void register(ClientCallbackInterface callbackInterface) throws RemoteException;

    void state() throws RemoteException;
}
