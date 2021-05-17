package client;

import peer.RemotePeer;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestApp {
    public static void main(String[] args) throws RemoteException {
        if (args.length < 2) {
            System.out.println("Usage: java client.Client <SAP> <OPERATION> <PARAM1> <PARAM2> ...");
            return;
        }
        String peer = args[0];
        String operation = args[1];

        RemotePeer stub;

        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            stub = (RemotePeer) registry.lookup(peer);
        } catch (Exception e) {
            System.err.println("Client exception: " + e);
            e.printStackTrace();
            return;
        }

        switch (operation) {
            case "CHORD":
                System.out.println(stub.chord());
                break;
            case "BACKUP":
                System.out.println(stub.backup(args[2], Integer.parseInt(args[3])));
                break;
            case "LOOKUP":
                System.out.println(stub.findSuccessor(Integer.parseInt(args[2])));
                break;
            case "STATE":
                System.out.println(stub.state());
                break;
            case "RESTORE":
                System.out.println(stub.restore(args[2]));
                break;
            case "DELETE":
                System.out.println(stub.delete(args[2]));
                break;
            default:
                System.out.println("Invalid Operation!");
        }
    }
}
