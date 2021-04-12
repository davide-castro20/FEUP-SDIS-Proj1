package g03.Protocols;

import g03.FileInfo;
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

            if (this.peer.getChunks().containsKey(key)) {
                actualRepDegree = this.peer.getChunks().get(key).getPerceivedReplicationDegree();

            } else if(this.peer.getFiles().containsKey(message.getFileId())) { //peer that is sending the chunk has the original file
                actualRepDegree = this.peer.getFiles().get(message.getFileId()).getChunksPeers().get(message.getChunkNumber()).getPerceivedReplicationDegree();
            }
            if (currentIteration < maxIterations && actualRepDegree < desiredReplicationDegree) {
                System.out.println("SENDING PUTCHUNK " + key);
                this.peer.getMDB().send(message);
                this.peer.getBackupPool().schedule(this, (int)Math.pow(2, currentIteration), TimeUnit.SECONDS);
            } else { //set this chunk as sent
                if(this.peer.getFiles().containsKey(message.getFileId())) {
                    FileInfo fileInfo = peer.getFiles().get(message.getFileId());
                    fileInfo.getChunksPeers().get(message.getChunkNumber()).setSent();

                    //check if operation is over
                    if(fileInfo.allSent())
                        peer.getOngoing().remove("backup-" + fileInfo.getPath() + "-" + fileInfo.getDesiredReplicationDegree());
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
