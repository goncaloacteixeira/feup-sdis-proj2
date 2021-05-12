package client;

import peer.RemoteInterface;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestApp {
    public static void main(String[] args) {
        String peerId = "peer0";
        RemoteInterface stub;

        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            stub = (RemoteInterface) registry.lookup(peerId);
        } catch (Exception e) {
            System.err.println("Client exception: " + e);
            e.printStackTrace();
            return;
        }

        try {
            stub.backup("teste.vdsx");
            //System.out.println(stub.chord());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
