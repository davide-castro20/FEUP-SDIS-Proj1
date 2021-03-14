import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Channel {

    MulticastSocket socket;
    InetAddress group;
    int port;

    public Channel(String address, int port) throws IOException {
        this.port = port;
        this.socket = new MulticastSocket(this.port);
        this.group = InetAddress.getByName(address);
        socket.joinGroup(this.group);
    }

    public void send(Message message) throws IOException {
        byte[] toSend = message.toByteArray();
        DatagramPacket packet = new DatagramPacket(toSend, toSend.length, this.group, this.port);
        this.socket.send(packet);
    }

    public byte[] receive() throws IOException {
        byte[] mrbuf = new byte[65000];
        this.socket.receive(new DatagramPacket(mrbuf, mrbuf.length));
        return mrbuf;
    }
}
