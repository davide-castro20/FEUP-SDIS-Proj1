package g03.Channels;

import g03.Enchancements.Enhancements;
import g03.Messages.Message;
import g03.Messages.MessageType;
import g03.Peer;

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
//                System.out.println(Arrays.toString(packet));
                Message m = new Message(packet);
                if (m.getType() == MessageType.UNKNOWN) {
                    System.out.println("RECEIVED UNKNOWN MESSAGE. IGNORING...");
                    continue;
                }

                if(Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.DELETE))
                    peer.checkDeleted(m);
//                System.out.println(m.toString());
                if (m.getSenderId() != peer.getId() && m.getType() == MessageType.PUTCHUNK) {
                    System.out.println("RECEIVED PUTCHUNK " + m.getFileId() + "-" + m.getChunkNumber() + " FROM " + m.getSenderId());
                    peer.receive(m);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
