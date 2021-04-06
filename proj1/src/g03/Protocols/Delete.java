package g03.Protocols;

import g03.FileInfo;
import g03.Message;
import g03.MessageType;
import g03.Peer;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

public class Delete implements Runnable {
    private final Peer peer;
    private final String path;

    public Delete(Peer peer, String path) {
        this.peer = peer;
        this.path = path;
    }

    @Override
    public void run() {

        Stream<Map.Entry<String, FileInfo>> matches = this.peer.getFiles().entrySet().stream().filter(f -> f.getValue().getPath().equals(path));
        if(matches.count() > 0) {
            matches.forEach((match) -> {

                String hash = match.getValue().getHash();
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
            });
        } else {
            System.err.println("File not found in backup system");
        }
    }
}
