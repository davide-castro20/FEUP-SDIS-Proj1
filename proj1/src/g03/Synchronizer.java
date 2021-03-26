package g03;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

public class Synchronizer implements Runnable {
    Peer peer;

    public Synchronizer(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        try(FileOutputStream fileOutChunks = new FileOutputStream("chunkData");
            ObjectOutputStream outChunks = new ObjectOutputStream(fileOutChunks))
        {
            outChunks.writeObject(peer.getChunks());

        } catch (IOException e) {
            e.printStackTrace();
        }

        try(FileOutputStream fileOutFiles = new FileOutputStream("fileData");
            ObjectOutputStream outFiles = new ObjectOutputStream(fileOutFiles))
        {
            outFiles.writeObject(peer.getFiles());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
