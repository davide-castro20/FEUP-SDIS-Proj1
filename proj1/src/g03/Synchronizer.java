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
        for(String path : peer.getFiles().keySet()) {
            File file = new File(path);
            if(!file.exists()) { //TODO: check if file is modified (create hash with metadata)
                peer.delete(path);
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
