package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientCallbackInterface extends Remote {
    void notify(String message) throws RemoteException;
}
