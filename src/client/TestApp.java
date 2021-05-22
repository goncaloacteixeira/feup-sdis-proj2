package client;

import peer.RemotePeer;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Test Application used to start requests on the P2P system
 */
public class TestApp implements ClientCallbackInterface {
    @Override
    public void notify(String message) {
        System.out.println(message);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    public static void main(String[] args) throws RemoteException {
        if (args.length < 2) {
            System.out.println("Usage: java client.Client <SAP> <OPERATION> <PARAM1> <PARAM2> ...");
            return;
        }

        Registry registry = LocateRegistry.getRegistry("localhost");

        ClientCallbackInterface callbackInterface = new TestApp();
        UnicastRemoteObject.exportObject(callbackInterface, 0);

        String peer = args[0];
        String operation = args[1];

        RemotePeer stub;

        try {
            stub = (RemotePeer) registry.lookup(peer);
            stub.register(callbackInterface);
        } catch (Exception e) {
            System.err.println("Client exception: " + e);
            e.printStackTrace();
            return;
        }

        switch (operation) {
            case "CHORD":
                stub.chord();
                break;
            case "BACKUP":
                stub.backup(args[2], Integer.parseInt(args[3]));
                break;
            case "LOOKUP":
                stub.clientFindSuccessor(Integer.parseInt(args[2]));
                break;
            case "STATE":
                stub.state();
                break;
            case "RESTORE":
                stub.restore(args[2]);
                break;
            case "DELETE":
                stub.delete(args[2]);
                break;
            case "RECLAIM":
                stub.reclaim(Long.parseLong(args[2]));
                break;
            default:
                System.out.println("Invalid Operation!");
        }

        System.out.print("Sent Request to Peer! Waiting reply...\r");

        while (true)
            ;
    }
}
