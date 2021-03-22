package g03;

import java.util.ArrayList;
import java.util.List;

public class Chunk {

    String fileId;
    int chunkNumber;
    int desiredReplicationDegree;
    int perceivedReplicationDegree;

    public Chunk(String fileId, int chunkNumber, int perceivedReplicationDegree, int desiredReplicationDegree) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.perceivedReplicationDegree = perceivedReplicationDegree;
    }

    public int getPerceivedReplicationDegree() {
        return this.perceivedReplicationDegree;
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
}
