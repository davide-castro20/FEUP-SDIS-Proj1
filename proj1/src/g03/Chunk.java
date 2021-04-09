package g03;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Chunk implements Serializable, Comparable<Chunk> {

    String fileId;
    int chunkNumber;
    int desiredReplicationDegree;
    Set<Integer> peers;
    int size;
    boolean sent = false;

    public Chunk(String fileId, int chunkNumber, int desiredReplicationDegree, int size) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.peers = ConcurrentHashMap.newKeySet();
        this.size = size;
    }

    public int getPerceivedReplicationDegree() { return this.peers.size(); }

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

    public void addPeer(Integer newPeer) {
        peers.add(newPeer);
    }

    public void removePeer(Integer peerToRemove) { peers.remove(peerToRemove); }

    @Override
    public int compareTo(Chunk o) {
        int excessReplication = this.peers.size() - this.desiredReplicationDegree;
        int otherExcess = o.getPerceivedReplicationDegree() - o.getDesiredReplicationDegree();

        if(excessReplication == otherExcess) {
            long thisSize = size;
            long otherSize = o.getSize();

            try {
                return Math.toIntExact(thisSize - otherSize);
            } catch (Exception ignore) {
                return 0;
            }
        } else
            return otherExcess - excessReplication;
    }

    public boolean isSent() { return sent; }

    public void setSent() { sent = true; }
}
