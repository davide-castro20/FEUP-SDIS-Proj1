package g03.Protocols;

import g03.FileInfo;
import g03.Message;
import g03.MessageType;
import g03.Peer;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Restore implements Runnable {
    private final Peer peer;
    private final String path;

    public Restore(Peer peer, String path) {
        this.peer = peer;
        this.path = path;
    }

    @Override
    public void run() {
        Stream<Map.Entry<String, FileInfo>> matches = this.peer.getFiles().entrySet().stream().filter(f -> f.getValue().getPath().equals(path));

        FileInfo file = matches.findFirst().get().getValue();
        if(file == null) {
            System.err.println("File not found in backup system");
        }
        String hash = file.getHash();

        this.peer.getChunksToRestore().put(hash, IntStream.range(0, file.getChunkAmount()).boxed().collect(Collectors.toList()));

        for (int i = 0; i < file.getChunkAmount(); i++) {
            String[] msgArgs = {this.peer.getProtocolVersion(),
                    String.valueOf(this.peer.getId()),
                    hash,
                    String.valueOf(i)};

            Message msgToSend = new Message(MessageType.GETCHUNK, msgArgs, null);

            try {
                this.peer.getMC().send(msgToSend);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
