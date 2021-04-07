package g03.Protocols;

import g03.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
                if(Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.RESTORE)) {
                    ScheduledFuture<?> tcp_task = peer.getPool().schedule(new TCPInitiator(peer, hash, i), 0, TimeUnit.MICROSECONDS);
                    peer.getTcpConnections().put(hash + "-" + i, tcp_task);
                }

                this.peer.getMC().send(msgToSend);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
