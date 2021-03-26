package g03.Protocols;

import g03.*;

import java.io.FileInputStream;
import java.io.IOException;

public class Backup implements Runnable {
    private final Peer peer;
    private final String path;
    private final int replicationDegree;

    public Backup(Peer peer, String path, int replicationDegree) {
        this.peer = peer;
        this.path = path;
        this.replicationDegree = replicationDegree;
    }

    @Override
    public void run() {
        String hash = Peer.getFileIdString(path);
        String[] msgArgs = {this.peer.getProtocolVersion(),
                String.valueOf(this.peer.getId()),
                hash,
                "0", // CHUNK NO
                String.valueOf(replicationDegree)};

        //TODO: Last chunk with 0 bytes
        byte[] data;
        int nRead = -1;
        int nChunk = 0;
        try (FileInputStream file = new FileInputStream(path)) {
            while (nRead != 0) {
                data = new byte[64000];
                nRead = file.read(data, 0, 64000);
                System.out.println(nRead);
                if (nRead < 64000) nRead = 0;
                msgArgs[3] = String.valueOf(nChunk); // set chunk number
                nChunk++;
                Message msgToSend = new Message(MessageType.PUTCHUNK, msgArgs, data);
                this.peer.getPool().execute(new PutChunkMessageSender(this.peer, msgToSend, replicationDegree, 5));
            }

            if (!this.peer.getFiles().containsKey(path)) {
                this.peer.getFiles().put(path, new FileInfo(path, hash, replicationDegree, nChunk));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
