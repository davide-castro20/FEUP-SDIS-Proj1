package g03;

import java.io.File;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Chunk implements Serializable, Comparable<Chunk> {

    String fileId;
    int chunkNumber;
    int desiredReplicationDegree;
    Set<Integer> peers;
    int size;

    public Chunk(String fileId, int chunkNumber, int desiredReplicationDegree, int size) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.peers = ConcurrentHashMap.newKeySet();
        this.size = size;
    }

    public int getPerceivedReplicationDegree() {
        return this.peers.size() + 1;
    }

    public String getFileId() {
        return fileId;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public int getDesiredReplicationDegree() {
        return desiredReplicationDegree;
    }

    public Set<Integer> getPeers() {
        return peers;
    }

    public int getSize() { return size; }

    @Override
    public int compareTo(Chunk o) {
        int excessReplication = this.peers.size() + 1 - this.desiredReplicationDegree;
        int otherExcess = o.getPerceivedReplicationDegree() - o.getDesiredReplicationDegree();

        if(excessReplication == otherExcess) {
            long thisSize = new File(this.fileId + "-" + this.chunkNumber).length();
            long otherSize = new File(o.getFileId() + "-" + o.getChunkNumber()).length();

            try{
                return Math.toIntExact(thisSize - otherSize);
            } catch (Exception ignore) {
                return 0;
            }
        } else
            return otherExcess - excessReplication;
    }
}
