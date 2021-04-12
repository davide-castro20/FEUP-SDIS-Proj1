package g03.Channels;

import g03.*;
import g03.Enchancements.Enhancements;
import g03.Messages.Message;
import g03.Messages.MessageType;
import g03.Protocols.ChunkMessageSender;
import g03.Protocols.PutChunkMessageSender;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MC implements Runnable {

    private final Peer peer;

    public MC(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        while (true) {
            try {
                byte[] packet = peer.getMC().receive();
                if (packet == null)
                    continue;

                Message message = new Message(packet);
                if (message.getType() == MessageType.UNKNOWN) {
                    System.out.println("RECEIVED UNKNOWN MESSAGE. IGNORING...");
                    continue;
                }

                if (message.getSenderId() == peer.getId())
                    continue;

                if(Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.DELETE))
                    peer.checkDeleted(message);

                Runnable run = null;
                switch (message.getType()) {
                    case STORED:
                        run = () -> {
                            String key = message.getFileId() + "-" + message.getChunkNumber();

                            System.out.println("RECEIVED STORED " + key + " FROM " + message.getSenderId());


                            if (peer.getChunks().containsKey(key)) {
                                Chunk c = peer.getChunks().get(key);
                                c.getPeers().add(message.getSenderId());
                            } else if (peer.getFiles().containsKey(message.getFileId())) { //if this peer has the original file
                                peer.getFiles().get(message.getFileId()).getChunksPeers().get(message.getChunkNumber()).addPeer(message.getSenderId());
                            }
                        };
                        peer.getBackupPool().execute(run);
                        break;
                    case GETCHUNK:
                        run = () -> {
                            String key = message.getFileId() + "-" + message.getChunkNumber();
                            if (peer.getChunks().containsKey(key)) {
                                String[] msgArgs = {peer.getProtocolVersion(),
                                        String.valueOf(peer.getId()),
                                        message.getFileId(),
                                        String.valueOf(message.getChunkNumber())};

                                byte[] body = null;
                                int port = -1;
                                System.out.println("RECEIVED GETCHUNK " + key + " FROM " + message.getSenderId());
                                if (Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.RESTORE)
                                        && Peer.supportsEnhancement(message.getProtocolVersion(), Enhancements.RESTORE)) {
                                    port = message.getPort();
                                } else {
                                    try (FileInputStream file = new FileInputStream("backup/" + key)) {
                                        body = file.readAllBytes();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }

                                //Schedule the CHUNK message
                                Message msgToSend = new Message(MessageType.CHUNK, msgArgs, body);
                                ScheduledFuture<?> task = peer.getRestorePool().schedule(
                                        new ChunkMessageSender(peer, msgToSend, port),
                                        new Random().nextInt(400),
                                        TimeUnit.MILLISECONDS);
                                peer.getMessagesToSend().put(key, task);

                            }
                        };
                        peer.getRestorePool().execute(run);
                        break;
                    case DELETE:
                        run = () -> {
                            peer.getChunks().forEach((key, value) -> {
                                if (key.startsWith(message.getFileId()) && peer.getChunks().remove(key, value)) {
                                    System.out.println("DELETING CHUNK " + key);
                                    File chunkToDelete = new File("backup/" + key);
                                    chunkToDelete.delete();
                                    peer.removeSpace(value.getSize());
                                }
                            });

                            if (Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.DELETE)) {

                                String[] msgArgs = {peer.getProtocolVersion(),
                                        String.valueOf(peer.getId()),
                                        message.getFileId()
                                };
                                Message msgToSend = new Message(MessageType.DELETED, msgArgs, null);
                                peer.getDeletePool().execute(() -> {
                                    try {
                                        peer.getMC().send(msgToSend);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        };
                        peer.getDeletePool().execute(run);
                        break;
                    case DELETED:
                        run = () -> {
                            if (Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.DELETE)) {
                                if (peer.getPeersDidNotDeleteFiles().containsKey(message.getFileId())) {
                                    peer.getPeersDidNotDeleteFiles().get(message.getFileId()).remove(message.getSenderId());
                                    if (peer.getPeersDidNotDeleteFiles().get(message.getFileId()).size() == 0) {
                                        peer.getPeersDidNotDeleteFiles().remove(message.getFileId());
                                    }
                                }
                            }
                        };
                        peer.getDeletePool().execute(run);
                        break;
                    case REMOVED:
                        run = () -> {
                            String key = message.getFileId() + "-" + message.getChunkNumber();
                            if (peer.getChunks().containsKey(key)) {
                                peer.getChunks().get(key).getPeers().remove(message.getSenderId());

                                if (peer.getChunks().get(key).getPerceivedReplicationDegree() < peer.getChunks().get(key).getDesiredReplicationDegree()) {
                                    System.out.println("CHUNK " + key + " replication degree dropped below desired");
                                    byte[] data = new byte[64000];
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
                                        final Message msgToSend = new Message(MessageType.PUTCHUNK, msgArgs, data);

                                        if (peer.getStoppedMDB() && Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.BACKUP)) {
                                            peer.restartMDB();
                                        }

                                        ScheduledFuture<?> task = peer.getBackupPool().schedule(() -> {
                                            new PutChunkMessageSender(peer, msgToSend, peer.getChunks().get(key).getDesiredReplicationDegree(), 5).run();
                                            if (Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.BACKUP)) {
                                                if (!peer.getStoppedMDB() && peer.getCurrentSpace() == peer.getMaxSpace()) {
                                                    try {
                                                        peer.interruptMDB();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                        }, new Random().nextInt(400), TimeUnit.MILLISECONDS);
                                        peer.getBackupsToSend().put(key, task);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            } else if (peer.getFiles().containsKey(message.getFileId())) {
                                peer.getFiles().get(message.getFileId()).getChunksPeers().get(message.getChunkNumber()).removePeer(message.getSenderId());
                            }
                        };
                        peer.getReclaimPool().execute(run);
                        break;
                    default:
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
