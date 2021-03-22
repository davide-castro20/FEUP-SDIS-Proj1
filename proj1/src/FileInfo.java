public class FileInfo {

    private String path;
    private String hash;
    private int desiredReplicationDegree;
    private int chunkAmount;

    public FileInfo(String path, String hash, int desiredReplicationDegree, int chunkAmount) {
        this.path = path;
        this.hash = hash;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.chunkAmount = chunkAmount;
    }

    public void setChunkAmount(int chunkAmount) {
        this.chunkAmount = chunkAmount;
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
        return chunkAmount;
    }
}
