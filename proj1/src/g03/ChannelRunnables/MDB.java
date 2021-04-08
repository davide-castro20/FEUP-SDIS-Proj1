package g03.ChannelRunnables;

import g03.Message;
import g03.MessageType;
import g03.Peer;

import java.io.IOException;

public class MDB implements Runnable {

    private final Peer peer;

    public MDB(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        while (!peer.getStoppedMDB()) {
            try {
                byte[] packet = peer.getMDB().receive();
                if(packet == null)
                    continue;
                System.out.println(packet.toString());
                Message m = new Message(packet);
//                System.out.println(m.toString());
                if (m.getSenderId() != peer.getId() && m.getType() == MessageType.PUTCHUNK) {
                    peer.receive(m);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
