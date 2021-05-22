package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Callback Interface for the Peers to be able to send notifications to the Test Application
 */
public interface ClientCallbackInterface extends Remote {
    /**
     * Method to send a notification from the server to the client
     * @param message notification to be sent to the client
     * @throws RemoteException on error with RMI
     */
    void notify(String message) throws RemoteException;
}
