package g03.Channels;

import g03.Messages.Message;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class Channel {

    MulticastSocket socket;
    InetAddress group;
    int port;

    public Channel(String address, int port) throws IOException {
        this.port = port;
        this.socket = new MulticastSocket(this.port);
        this.group = InetAddress.getByName(address);
        socket.joinGroup(this.group);
        socket.setSoTimeout(100);
    }

    public void send(Message message) throws IOException {
        byte[] toSend = message.toByteArray();
        DatagramPacket packet = new DatagramPacket(toSend, toSend.length, this.group, this.port);
        this.socket.send(packet);
    }

    public byte[] receive() throws IOException {
        byte[] mrbuf = new byte[65000];
        DatagramPacket packet = new DatagramPacket(mrbuf, mrbuf.length);
        try {
            this.socket.receive(packet);
        } catch (SocketTimeoutException | SocketException ignored) { return null;}

        return Arrays.copyOf(mrbuf, packet.getLength());
    }

    public void leaveGroup() throws IOException {
        socket.leaveGroup(this.group);
        socket.close();
    }

    public void joinGroup() throws IOException {
        socket = new MulticastSocket(this.port);
        socket.joinGroup(this.group);
        socket.setSoTimeout(100);
    }
}
