package g03.Protocols;

import g03.Messages.Message;
import g03.Peer;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class ChunkMessageSender implements Runnable {

    private final Peer peer;
    private final Message toSend;
    private final int port;

    public ChunkMessageSender(Peer peer, Message message, int port) {
        this.peer = peer;
        this.toSend = message;
        this.port = port;
    }

    @Override
    public void run() {
        try {
            this.peer.getMDR().send(this.toSend);
            String key = this.toSend.getFileId() + "-" + this.toSend.getChunkNumber();
            this.peer.getMessagesToSend().remove(key);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(port != -1) {
            try (Socket socket = new Socket(InetAddress.getLocalHost(), port)){

                byte[] body;
                try (FileInputStream file = new FileInputStream("backup/" + toSend.getFileId() + "-" + toSend.getChunkNumber());
                     BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
                    body = file.readAllBytes();
                    out.write(body);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
