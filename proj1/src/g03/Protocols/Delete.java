package g03.Protocols;

import g03.FileInfo;
import g03.Message;
import g03.MessageType;
import g03.Peer;

import java.io.File;
import java.io.IOException;

public class Delete implements Runnable {
    private final Peer peer;
    private final String path;

    public Delete(Peer peer, String path) {
        this.peer = peer;
        this.path = path;
    }

    @Override
    public void run() {
        if (!this.peer.getFiles().containsKey(path)) {
            System.err.println("File not found");
            return;
        }

        FileInfo file = this.peer.getFiles().get(path);
        String hash = file.getHash();

        String[] msgArgs = {this.peer.getProtocolVersion(),
                String.valueOf(this.peer.getId()),
                hash};
        Message deleteMsg = new Message(MessageType.DELETE, msgArgs, null);

        try {
            this.peer.getMC().send(deleteMsg);

            this.peer.getFiles().remove(path);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
