package g03.Protocols;

import g03.Messages.Message;
import g03.Peer;

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

            } else if(this.peer.getFiles().containsKey(message.getFileId())) { //peer that is sending the chunk has the original file
                actualRepDegree = this.peer.getFiles().get(message.getFileId()).getChunksPeers().get(message.getChunkNumber()).getPerceivedReplicationDegree();
            }
            if (currentIteration < maxIterations && actualRepDegree < desiredReplicationDegree) {
                System.out.println("SENDING ^");
                this.peer.getMDB().send(message);
                this.peer.getPool().schedule(this, (int)Math.pow(2, currentIteration), TimeUnit.SECONDS);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
