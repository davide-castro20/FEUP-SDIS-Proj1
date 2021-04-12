package g03.Protocols;

import g03.Chunk;
import g03.Messages.Message;
import g03.Messages.MessageType;
import g03.Peer;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class Reclaim implements Runnable {
    private final Peer peer;
    private final long space;

    public Reclaim(Peer peer, long space) {
        this.peer = peer;
        this.space = space;
    }

    @Override
    public void run() {
        peer.getChunks().entrySet().stream().sorted(Map.Entry.comparingByValue())
                .takeWhile(m -> peer.getCurrentSpace() > space)
                .forEach(this::removeChunk);
        peer.setMaxSpace(space);
        peer.getOngoing().remove("reclaim-" + space);
    }

    private void removeChunk(Map.Entry<String, Chunk> stringChunkEntry) {
        String[] msgArgs = {peer.getProtocolVersion(),
                String.valueOf(peer.getId()),
                stringChunkEntry.getValue().getFileId(),
                String.valueOf(stringChunkEntry.getValue().getChunkNumber())};
        Message msgToSend = new Message(MessageType.REMOVED, msgArgs, null);

        String chunkName = stringChunkEntry.getValue().getFileId() + "-"
                + stringChunkEntry.getValue().getChunkNumber();

        try {
            File fileToRemove = new File("backup/" + chunkName);

            if (fileToRemove.delete()) {
                System.out.println("SENDING REMOVED " + chunkName);
                peer.removeSpace(peer.getChunks().get(stringChunkEntry.getKey()).getSize());
                peer.getChunks().remove(stringChunkEntry.getKey());
                try {
                    peer.getMC().send(msgToSend);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
