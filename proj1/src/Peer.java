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

//        System.out.println(peer.getFileIdString("teste.txt"));
//        if (peer.id == 1) {
//            peer.backup("teste.txt", 1);
//        }

        if (peer.id == 2) {
            peer.receive();
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

    public void receive() throws IOException {

        byte[] received = this.MDBChannel.receive();
        Message m = new Message(received);

        //TODO: change to get methods
        FileOutputStream out = new FileOutputStream(m.fileId + "-" + m.chunkNumber);
        out.write(m.body);
        out.close();
    }

    @Override
    public void backup(String path, int replicationDegree) throws RemoteException {

        String[] msgArgs = {this.protocolVersion,
                String.valueOf(this.id),
                this.getFileIdString(path),
                "0", // CHUNK NO
                String.valueOf(replicationDegree)};


        String headerString = this.protocolVersion +
                " " +
                "PUTCHUNK" +
                " " +
                this.id + //PeerId
                " " +
                this.getFileIdString(path) + //FileId
                " " +
                0 + // CHUNK No
                " " +
                replicationDegree +
                " \r\n\r\n";

        byte[] header = headerString.getBytes();

        byte[] data = null;
        try {
            FileInputStream file = new FileInputStream(path);
            data = new byte[file.available()];
            file.read(data, 0, file.available());
            file.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        assert data != null; // ?
        byte[] toSend = new byte[header.length + data.length];
        System.arraycopy(header, 0, toSend, 0, header.length);
        System.arraycopy(data, 0, toSend, header.length, data.length);

        Message msgToSend = new Message(toSend);

        try {
            this.MDBChannel.send(msgToSend);
        } catch (IOException e) {
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




























