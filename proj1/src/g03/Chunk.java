package g03;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Chunk {

    String fileId;
    int chunkNumber;
    int desiredReplicationDegree;
    Set<Integer> peers;

    public Chunk(String fileId, int chunkNumber, int desiredReplicationDegree) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.peers = ConcurrentHashMap.newKeySet();
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
}
