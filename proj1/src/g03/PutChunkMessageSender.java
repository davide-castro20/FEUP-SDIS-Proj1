package g03;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class PutChunkMessageSender implements Runnable {

    private final Peer peer;
    private final Message message;
    private final int desiredReplicationDegree;
    private final int maxIterations;
    private int currentIteration;
    private int actualRepDegree;

    public PutChunkMessageSender(Peer peer, Message message, int desiredReplicationDegree, int maxIterations) {
        this.peer = peer;
        this.message = message;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.maxIterations = maxIterations;
        this.currentIteration = -1;
        this.actualRepDegree = 0;
    }

    @Override
    public void run() {
        try {
            currentIteration++;
            String key = message.getFileId() + "-" + message.getChunkNumber();
            System.out.println("PUTCHUNK " + key);
            if (this.peer.getChunks().containsKey(key)) {
                actualRepDegree = this.peer.getChunks().get(key).getPerceivedReplicationDegree();

            } else if(this.peer.getSentChunksStatus().containsKey(message.getFileId())) { //peer that is sending the chunk has the original file
                actualRepDegree = this.peer.getSentChunksStatus().get(message.getFileId()).get(message.getChunkNumber());
            }
            if (currentIteration < maxIterations && actualRepDegree < desiredReplicationDegree) {
                System.out.println("SENDING ^");
                this.peer.getMDB().send(message);
                this.peer.getPool().schedule(this, (int)Math.pow(2, currentIteration), TimeUnit.SECONDS);
            } else {
                this.peer.getSentChunksStatus().get(message.getFileId()).set(message.getChunkNumber(), 0);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
