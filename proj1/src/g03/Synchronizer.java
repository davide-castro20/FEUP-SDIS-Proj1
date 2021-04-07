package g03;

import java.io.*;
import java.rmi.RemoteException;
import java.util.Map;

public class Synchronizer implements Runnable {
    Peer peer;

    public Synchronizer(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        checkFiles();
        writeChunkData();
        writeFileData();
    }

    private void checkFiles() {
        for(FileInfo fileInfo : peer.getFiles().values()) {
            File file = new File(fileInfo.getPath());
            if(!file.exists() || !Peer.getFileIdString(fileInfo.getPath()).equals(fileInfo.getHash())) {
                peer.delete(fileInfo.getPath());
            }
        }
    }

    private void writeChunkData() {
        try(FileOutputStream fileOutChunks = new FileOutputStream("chunkData");
            ObjectOutputStream outChunks = new ObjectOutputStream(fileOutChunks))
        {
            outChunks.writeObject(peer.getChunks());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeFileData() {
        try(FileOutputStream fileOutFiles = new FileOutputStream("fileData");
            ObjectOutputStream outFiles = new ObjectOutputStream(fileOutFiles))
        {
            outFiles.writeObject(peer.getFiles());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
