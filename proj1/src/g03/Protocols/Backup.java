package g03.Protocols;

import g03.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

        File fileToBackup = new File(path);
        if(!fileToBackup.exists() || fileToBackup.isDirectory() || fileToBackup.length() > 64000000000L)
            return;

        String hash = Peer.getFileIdString(path);
        String[] msgArgs = {this.peer.getProtocolVersion(),
                String.valueOf(this.peer.getId()),
                hash,
                "0", // CHUNK NO
                String.valueOf(replicationDegree)};

        byte[] data;
        int nRead = -1;
        int nChunk = 0;
        try (FileInputStream file = new FileInputStream(path)) {
            while (nRead != 0) {
                data = new byte[64000];
                nRead = file.read(data, 0, 64000);
                if(nRead == -1)
                    nRead = 0;
                System.out.println(nRead);
                Message msgToSend;
                msgArgs[3] = String.valueOf(nChunk); // set chunk number
                nChunk++;
                if (nRead < 64000) {
                    byte[] dataToSend = new byte[nRead];
                    System.arraycopy(data, 0, dataToSend, 0, nRead);
                    msgToSend = new Message(MessageType.PUTCHUNK, msgArgs, dataToSend);
                    nRead = 0; // Used to terminate the loop if the the last chunk doesn't have 64 KB
                } else {
                    msgToSend = new Message(MessageType.PUTCHUNK, msgArgs, data);
                }
                this.peer.getPool().execute(new PutChunkMessageSender(this.peer, msgToSend, replicationDegree, 5));
            }

            //TODO: Maybe wait for threads
            FileInfo fileInfo = new FileInfo(path, hash, replicationDegree, nChunk);
            if(this.peer.getFiles().containsKey(hash)) {
                fileInfo.setChunksPeers(this.peer.getFiles().get(hash).getChunksPeers());
            }
            this.peer.getFiles().put(hash, fileInfo);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
