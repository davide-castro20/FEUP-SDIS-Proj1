package g03.Channels;

import g03.Messages.Message;
import g03.Messages.MessageType;
import g03.Peer;

import java.util.Arrays;

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
                System.out.println(Arrays.toString(packet));
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
