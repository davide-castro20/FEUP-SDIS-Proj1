import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Peer implements PeerStub {

    int id;
    String protocolVersion;
    String serviceAccessPointName;

    Channel MCChannel;
    Channel MDBChannel;
    Channel MDRChannel;


    public static void main(String[] args) throws IOException, AlreadyBoundException {
        if (args.length != 6) {
            System.out.println("Usage: java Peer <protocol_version> <peer_id> <service_access_point> <MC_address>:<MC_Port> <MDB_address>:<MDB_Port> <MDR_address>:<MDR_Port>");
        }

        String protocolVersion = args[0];
        int peerId = Integer.parseInt(args[1]);
        String serviceAccessPointName = args[2];

        String[] MCinfo = args[3].split(":");
        Channel MCchannel = new Channel(MCinfo[0], Integer.parseInt(MCinfo[1]));

        String[] MDBinfo = args[4].split(":");
        Channel MDBchannel = new Channel(MDBinfo[0], Integer.parseInt(MDBinfo[1]));

        String[] MDRinfo = args[5].split(":");
        Channel MDRchannel = new Channel(MDRinfo[0], Integer.parseInt(MDRinfo[1]));

        Peer peer = new Peer(peerId, protocolVersion, serviceAccessPointName, MCchannel, MDBchannel, MDRchannel);
        peer.bindRMI();

        System.out.println(peer.getFileIdString("davinki.mp3"));
//        if (peer.id == 1) {
//            peer.backup("davinki.mp3", 1);
//        }

        if (peer.id == 2) {
            byte[] received;
            while ((received = peer.MDBChannel.receive()) != null) {
                byte[] finalReceived = received;
                new Thread(() -> {
                    try {
                        peer.receive(finalReceived);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();

            }
        }


    }

    public Peer(int id, String protocolVersion, String serviceAccessPointName, Channel MCChannel, Channel MDBChannel, Channel MDRChannel) {
        this.id = id;
        this.protocolVersion = protocolVersion;
        this.serviceAccessPointName = serviceAccessPointName;
        this.MCChannel = MCChannel;
        this.MDBChannel = MDBChannel;
        this.MDRChannel = MDRChannel;
    }

    public void bindRMI() throws RemoteException, AlreadyBoundException {
        PeerStub stub = (PeerStub) UnicastRemoteObject.exportObject(this, 0);

        // Bind the remote object's stub in the registry
        Registry registry = LocateRegistry.getRegistry();
        registry.bind(this.serviceAccessPointName, stub);
    }

    public void receive(byte[] received) throws IOException {

        Message m = new Message(received);

        //TODO: change to get methods
        FileOutputStream out = new FileOutputStream(m.fileId + "-" + m.chunkNumber);
        out.write(m.body);
        out.close();

        Message reply = new Message(MessageType.STORED,
                new String[]{
                        String.valueOf(this.protocolVersion),
                        String.valueOf(this.id), m.fileId,
                        String.valueOf(m.chunkNumber)},
                null);

        this.MCChannel.send(reply);

    }

    @Override
    public void backup(String path, int replicationDegree) throws RemoteException {

        String[] msgArgs = {this.protocolVersion,
                String.valueOf(this.id),
                this.getFileIdString(path),
                "0", // CHUNK NO
                String.valueOf(replicationDegree)};

        byte[] data = null;
        int nRead = -1;
        int nChunk = 0;
        try {
            FileInputStream file = new FileInputStream(path);
            while (nRead != 0) {
                data = new byte[64000];
                nRead = file.read(data, 0, 64000);
                System.out.println(nRead);
                if (nRead < 64000) nRead = 0;
                msgArgs[3] = String.valueOf(nChunk); // set chunk number
                nChunk++;
                Message msgToSend = new Message(MessageType.PUTCHUNK, msgArgs, data);
                try {
                    this.MDBChannel.send(msgToSend);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            file.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void restore(String path) throws RemoteException {

    }

    @Override
    public void delete(String path) throws RemoteException {

    }

    @Override
    public void reclaim(long amountOfBytes) throws RemoteException {

    }

    @Override
    public void state() throws RemoteException {

    }

    private String getFileIdString(String path) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] hash = digest.digest(path.getBytes());

        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit((b & 0xF), 16));
        }

        return result.toString();
    }
}




























