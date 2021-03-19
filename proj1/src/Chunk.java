import java.util.ArrayList;
import java.util.List;

public class Chunk {

    String fileId;
    int chunkNumber;
    int desiredReplicationDegree;
    List<Integer> peers;

    public Chunk(String fileId, int chunkNumber, int desiredReplicationDegree) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.peers = new ArrayList<>();
    }

    public int getPerceivedReplicationDegree() {
        return peers.size();
    }

    public void addPeer(int peerId) {
        if (!peers.contains(peerId)) {
            peers.add(peerId);
        }
    }
}
