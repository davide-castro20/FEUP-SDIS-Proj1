package g03.Protocols;

import g03.Chunk;
import g03.Message;
import g03.MessageType;
import g03.Peer;

import java.util.Map;

public class Reclaim implements Runnable {
    private final Peer peer;
    private long space;

    public Reclaim(Peer peer, long space) {
        this.peer = peer;
        this.space = space;
    }

    @Override
    public void run() {
        peer.getChunks().entrySet().stream().sorted(Map.Entry.comparingByValue())
                .takeWhile(m -> peer.getCurrentSpace() > space)
                .forEach(this::removeChunk);
    }

    private void removeChunk(Map.Entry<String, Chunk> stringChunkEntry) {
        String[] msgArgs = {peer.getProtocolVersion(),
                String.valueOf(peer.getId()),
                stringChunkEntry.getValue().getFileId(),
                String.valueOf(stringChunkEntry.getValue().getChunkNumber())};
        Message msgToSend = new Message(MessageType.REMOVED, msgArgs, null);
        
    }
}
