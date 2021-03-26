package g03.ChannelRunnables;

import g03.Message;
import g03.Peer;

import java.io.IOException;

public class MDB implements Runnable {

    private final Peer peer;

    public MDB(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message m = new Message(peer.getMDB().receive());
                if (m.getSenderId() != peer.getId()) {
                    peer.receive(m);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
