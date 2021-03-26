package g03.ChannelRunnables;

import g03.Message;
import g03.Peer;

import java.io.IOException;

public class MDR implements Runnable {

    private final Peer peer;

    public MDR(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message m = new Message(peer.getMDR().receive());
                if (m.getSenderId() != peer.getId()) {
                    String key = m.getFileId() + "-" + m.getChunkNumber();
                    if (peer.getMessagesToSend().containsKey(key)) {
                        peer.getMessagesToSend().get(key).cancel(false);
                        peer.getMessagesToSend().remove(key);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
