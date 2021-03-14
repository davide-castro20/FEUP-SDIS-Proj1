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

    public void send(byte[] message) throws IOException {
        DatagramPacket packet = new DatagramPacket(message, message.length, this.group, this.port);
        this.socket.send(packet);
    }
}
