package g03.Protocols;

import g03.*;
import g03.Enchancements.Enhancements;
import g03.Enchancements.TCPInitiator;
import g03.Messages.Message;
import g03.Messages.MessageType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
            List<String> msgArgs = new ArrayList(Arrays.asList(this.peer.getProtocolVersion(),
                    String.valueOf(this.peer.getId()),
                    hash,
                    String.valueOf(i)));

            try {
                Message msgToSend;
                if(Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.RESTORE)) {
                    int port = peer.getTcp_ports().remove();
                    ScheduledFuture<?> tcp_task = peer.getRestorePool().schedule(new TCPInitiator(peer, hash, i, port), 0, TimeUnit.MICROSECONDS);
                    peer.getTcpConnections().put(hash + "-" + i, tcp_task);
                    msgArgs.add(Integer.toString(port));
                }
                msgToSend = new Message(MessageType.GETCHUNK, msgArgs.toArray(new String[0]), null);
                System.out.println("SENDING GETCHUNK " + Arrays.toString(msgArgs.toArray(new String[0])));
                this.peer.getMC().send(msgToSend);

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
