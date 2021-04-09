package g03;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestApp {

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 4) {
            System.out.println("Usage: java TestApp <peer_ap> <sub_protocol> <opnd_1> <opnd_2>");
        }

        String accessPoint = args[0];

        Registry registry;
        try {
            registry = LocateRegistry.getRegistry();
            PeerStub stub = (PeerStub) registry.lookup(accessPoint);

            String operation = args[1];
            if (operation.equalsIgnoreCase("BACKUP")) {
                stub.backup(args[2], Integer.parseInt(args[3]));
            } else if (operation.equalsIgnoreCase("RESTORE")) {
                stub.restore(args[2]);
            } else if (operation.equalsIgnoreCase("DELETE")) {
                stub.delete(args[2]);
            } else if (operation.equalsIgnoreCase("RECLAIM")) {
                stub.reclaim(Long.parseLong(args[2]));
            } else if (operation.equalsIgnoreCase("STATE")) {
                PeerState state = stub.state();
                System.out.println(state.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
