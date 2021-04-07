package g03.ChannelRunnables;

import g03.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MC implements Runnable {

    private final Peer peer;

    public MC(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message message = new Message(peer.getMC().receive());
                if(message.getSenderId() == peer.getId())
                    continue;
                //TODO: refactor - maybe change this to runnable classes
                Runnable run = null;
                switch(message.getType()) {
                    case STORED:
                         run = () -> {
                             String key = message.getFileId() + "-" + message.getChunkNumber();

                             System.out.println("RECEIVED STORED " + key + " FROM " + message.getSenderId());


                             if (peer.getChunks().containsKey(key)) {
                                Chunk c = peer.getChunks().get(key);
                                c.getPeers().add(message.getSenderId());
                             } else if(peer.getFiles().containsKey(message.getFileId())) { //if this peer has the original file
                                peer.getFiles().get(message.getFileId()).getChunksPeers().get(message.getChunkNumber()).addPeer(message.getSenderId());
                             }
//                            else { //IDK what this else means
//                                Chunk c = new Chunk(message.getFileId(), message.getChunkNumber(), message.getReplicationDegree());
//                                peer.getChunks().put(key, c);
//                            }
                        };
                        break;

                    case GETCHUNK:
                        run = () -> {
                            String key = message.getFileId() + "-" + message.getChunkNumber();
                            if (peer.getChunks().containsKey(key)) {
                                String[] msgArgs = {peer.getProtocolVersion(),
                                        String.valueOf(peer.getId()),
                                        message.getFileId(),
                                        String.valueOf(message.getChunkNumber())};

                                //TODO: refactor

                                byte[] body = null;
                                int port = -1;

                                if(Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.RESTORE)
                                        && Peer.supportsEnhancement(message.getProtocolVersion(), Enhancements.RESTORE)) {

                                    port = 40000 + message.getChunkNumber();


                                } else {
                                    try (FileInputStream file = new FileInputStream("backup/" + key)) {
                                        body = file.readAllBytes();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }

                                //Schedule the CHUNK message
                                Message msgToSend = new Message(MessageType.CHUNK, msgArgs, body);
                                ScheduledFuture<?> task = peer.getPool().schedule(new ChunkMessageSender(peer, msgToSend, port), new Random().nextInt(400), TimeUnit.MILLISECONDS);
                                peer.getMessagesToSend().put(key, task);

                            }
                        };
                        break;

                    case DELETE:
                        run = () ->
                            peer.getChunks().forEach((key, value) -> {
                                System.out.println("CHECKING FOR CHUNK TO DELETE " + key);
                                if (key.startsWith(message.getFileId()) && peer.getChunks().remove(key, value)) {
                                    System.out.println("DELETING CHUNK " + key);
                                    File chunkToDelete = new File("backup/" + key);
                                    chunkToDelete.delete();
                                    peer.removeSpace(value.getSize());
                                }
                            });

                        break;

                    case REMOVED:
                        run = () -> {
                            String key = message.getFileId() + "-" + message.getChunkNumber();
                            if (peer.getChunks().containsKey(key)) {
                                peer.getChunks().get(key).getPeers().remove(message.getSenderId());


                                //TODO: check if replication degree drops below desired (Kinda done)
                                if (peer.getChunks().get(key).getPerceivedReplicationDegree() < peer.getChunks().get(key).getDesiredReplicationDegree()) {
                                    System.out.println("CHUNK " + key + " replication degree dropped below desired");
                                    byte[] data = new byte[64000];
                                    Message msgToSend = null;
                                    try {
                                        System.out.println(peer.getId());
                                        String[] msgArgs = {peer.getProtocolVersion(),
                                                String.valueOf(peer.getId()),
                                                message.getFileId(),
                                                String.valueOf(message.getChunkNumber()),
                                                String.valueOf(peer.getChunks().get(key).getDesiredReplicationDegree())};

                                        try (FileInputStream fileIn = new FileInputStream("backup/" + key)) {
                                            int nRead = fileIn.read(data, 0, 64000);
                                            data = Arrays.copyOf(data, nRead);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        msgToSend = new Message(MessageType.PUTCHUNK, msgArgs, data);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        System.out.println("SCHEDULING PUTCHUNK WITH " + data.length + " BYTES OF DATA");
                                        ScheduledFuture<?> task = peer.getPool().schedule(
                                                new PutChunkMessageSender(peer, msgToSend, peer.getChunks().get(key).getDesiredReplicationDegree(), 5),
                                                new Random().nextInt(400), TimeUnit.MILLISECONDS);
                                        peer.getBackupsToSend().put(key, task);
                                        System.out.println(peer.getBackupsToSend().keySet().toString());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            } else { //TODO: maybe refactor

                            }
                        };
                        break;
                    default:
                        break;
                }
                if(run != null)
                    peer.getPool().execute(run);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
