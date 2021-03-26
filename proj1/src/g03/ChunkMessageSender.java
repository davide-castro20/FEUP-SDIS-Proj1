package g03;

import java.io.IOException;

public class ChunkMessageSender implements Runnable {

    private final Peer peer;
    private final Message toSend;

    public ChunkMessageSender(Peer peer, Message message) {
        this.peer = peer;
        this.toSend = message;
    }

    @Override
    public void run() {
        try {
            this.peer.getMDR().send(this.toSend);
            String key = this.toSend.fileId + "-" + this.toSend.chunkNumber;
            this.peer.getMessagesToSend().remove(key);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
