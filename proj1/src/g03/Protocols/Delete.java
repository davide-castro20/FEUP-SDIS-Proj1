package g03.Protocols;

import g03.Chunk;
import g03.Enchancements.Enhancements;
import g03.FileInfo;
import g03.Messages.Message;
import g03.Messages.MessageType;
import g03.Peer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

            if (Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.DELETE)) {
                Set<Integer> peers = ConcurrentHashMap.newKeySet();

                for(Chunk c : match.getValue().getChunksPeers()) {
                    peers.addAll(c.getPeers());
                }
                peer.getPeersDidNotDeleteFiles().put(hash, peers);
            }

            peer.getOngoing().remove("delete-" + path);

        });

        if(deleted.get()) {
            System.out.println("DELETED " + path);
        } else {
            System.err.println("File not found in backup system");
        }
    }
}
