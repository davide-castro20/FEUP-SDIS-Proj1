package g03.Protocols;

import g03.*;

import java.io.File;
import java.io.FileInputStream;

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

        //TODO: Verificar tamanho (max 64 GB)
        File fileToBackup = new File(path);
        if(!fileToBackup.exists() || fileToBackup.isDirectory())
            return;
        if (fileToBackup.length() > 64000000000L) {

        }

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
//            if (!this.peer.getFiles().containsKey(path)) {
                this.peer.getFiles().put(path, new FileInfo(path, hash, replicationDegree, nChunk));
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
