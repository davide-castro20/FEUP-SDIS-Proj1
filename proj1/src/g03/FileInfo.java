package g03;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FileInfo implements Serializable {

    private String path;
    private String hash;
    private int desiredReplicationDegree;
    private List<Chunk> chunksPeers;

    public FileInfo(String path, String hash, int desiredReplicationDegree, int chunkAmount) {
        this.path = path;
        this.hash = hash;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.chunksPeers = new ArrayList<>();
        for(int i = 0; i < chunkAmount; ++i) {
            chunksPeers.add(new Chunk(hash, i, desiredReplicationDegree, -1));
        }
    }

    public String getPath() {
        return path;
    }

    public String getHash() {
        return hash;
    }

    public int getDesiredReplicationDegree() {
        return desiredReplicationDegree;
    }

    public int getChunkAmount() {
        return chunksPeers.size();
    }

    public List<Chunk> getChunksPeers() {
        return chunksPeers;
    }
}
