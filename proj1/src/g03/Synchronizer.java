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
        writePeerState();
    }

    private void checkFiles() {
        for(FileInfo fileInfo : peer.getFiles().values()) {
            File file = new File(fileInfo.getPath());
            if(!file.exists() || !Peer.getFileIdString(fileInfo.getPath()).equals(fileInfo.getHash())) {
                peer.delete(fileInfo.getPath());
            }
        }
    }

    private void writePeerState() {
        try(FileOutputStream fileOutFiles = new FileOutputStream("peerState");
            ObjectOutputStream outFiles = new ObjectOutputStream(fileOutFiles))
        {
            PeerState peerState = peer.state();
            outFiles.writeObject(peerState);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
