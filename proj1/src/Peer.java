import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Peer {

    int id;
    String protocolVersion;
    String serviceAccessPointName;

    Channel MCChannel;
    Channel MDBChannel;
    Channel MDRChannel;


    public static void main(String[] args) throws IOException {
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
        System.out.println(peer.getFileIdString("teste.txt"));


        if (peer.id == 1) {
            peer.backup("teste.txt", 1);
        }

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

    public void receive() throws IOException {

        byte[] received = this.MDBChannel.receive();
        Message m = new Message(received);

        //TODO: change to get methods
        FileOutputStream out = new FileOutputStream(m.fileId + "-" + m.chunkNumber);
        out.write(m.body);
        out.close();
    }

    public void backup(String path, int replicationDegree) {

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

        byte[] toSend = new byte[header.length + data.length];
        System.arraycopy(header, 0, toSend, 0, header.length);
        System.arraycopy(data, 0, toSend, header.length, data.length);

        try {
            this.MDBChannel.send(toSend);
        } catch (IOException e) {
            e.printStackTrace();
        }

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




























