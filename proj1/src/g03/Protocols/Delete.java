package g03.Protocols;

import g03.FileInfo;
import g03.Message;
import g03.MessageType;
import g03.Peer;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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

        //TODO: maybe refactor (remember forEach() and count() are TERMINAL operations - WILL CONSUME the stream!)
        AtomicBoolean deleted = new AtomicBoolean(false);

        matches.forEach((match) -> {
            String hash = match.getValue().getHash();
            String[] msgArgs = {this.peer.getProtocolVersion(),
                    String.valueOf(this.peer.getId()),
                    hash};
            Message deleteMsg = new Message(MessageType.DELETE, msgArgs, null);

            try {
                this.peer.getMC().send(deleteMsg);

            } catch (Exception e) {
                e.printStackTrace();
            }

            this.peer.getFiles().remove(match.getKey());
            deleted.set(true);
        });

        if(deleted.get()) {
            System.out.println("DELETED " + path);
        } else {
            System.err.println("File not found in backup system");
        }
    }
}
