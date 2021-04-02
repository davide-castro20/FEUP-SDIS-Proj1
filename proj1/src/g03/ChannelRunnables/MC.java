package g03.ChannelRunnables;

import g03.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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
                Message message = new Message(peer.getMC().receive());
                //TODO: refactor - maybe change this to runnable classes
                switch(message.getType()) {
                    case STORED:
                        new Thread(() -> {
                            String key = message.getFileId() + "-" + message.getChunkNumber();

                            if (peer.getChunks().containsKey(key)) {
                                Chunk c = peer.getChunks().get(key);
                                c.getPeers().add(message.getSenderId());
                            } else { //IDK what this else means
                                Chunk c = new Chunk(message.getFileId(), message.getChunkNumber(), message.getReplicationDegree());
                                peer.getChunks().put(key, c);
                            }
                        }).start();
                        break;

                    case GETCHUNK:
                        new Thread(() -> {
                            String key = message.getFileId() + "-" + message.getChunkNumber();
                            if (peer.getChunks().containsKey(key)) {
                                String[] msgArgs = {peer.getProtocolVersion(),
                                        String.valueOf(peer.getId()),
                                        message.getFileId(),
                                        String.valueOf(message.getChunkNumber())};

                                //TODO: refactor
                                byte[] body = null;
                                try (FileInputStream file = new FileInputStream(key)) {
                                    body = file.readAllBytes();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                //Schedule the CHUNK message
                                Message msgToSend = new Message(MessageType.CHUNK, msgArgs, body);
                                ScheduledFuture<?> task = peer.getPool().schedule(new ChunkMessageSender(peer, msgToSend), new Random().nextInt(400), TimeUnit.MILLISECONDS);
                                peer.getMessagesToSend().put(key, task);
                            }
                        }).start();
                        break;

                    case DELETE:
                        new Thread(() -> peer.getChunks().forEach((key, value) -> {
                            if (key.startsWith(message.getFileId()) && peer.getChunks().remove(key, value)) {
                                File chunkToDelete = new File(key);
                                chunkToDelete.delete();
                            }
                        })).start();
                        break;

                    case REMOVED:
                        new Thread(() -> {
                            String key = message.getFileId() + "-" + message.getChunkNumber();
                            if (peer.getChunks().containsKey(key)) {
                                peer.getChunks().get(key).getPeers().remove(message.getSenderId());


                                //TODO: check if replication degree drops below desired (Kinda done)
                                if (peer.getChunks().get(key).getPerceivedReplicationDegree() < peer.getChunks().get(key).getDesiredReplicationDegree()) {
                                    String[] msgArgs = {peer.getProtocolVersion(),
                                            String.valueOf(peer.getId()),
                                            message.getFileId(),
                                            String.valueOf(message.getChunkNumber())};
                                    Message msgToSend = new Message(MessageType.PUTCHUNK, msgArgs, null);
                                    ScheduledFuture<?> task = peer.getPool().schedule(
                                            new PutChunkMessageSender(peer, msgToSend, peer.getChunks().get(key).getDesiredReplicationDegree(), 5),
                                            new Random().nextInt(400), TimeUnit.MILLISECONDS);
                                    peer.getBackupsToSend().put(key, task);
                                }
                            }
                        }).start();
                        break;

                }
//                if (message.getType() == MessageType.STORED) {
//                    String key = message.getFileId() + "-" + message.getChunkNumber();
//
//                    if (peer.getChunks().containsKey(key)) {
//                        Chunk c = peer.getChunks().get(key);
//                        c.getPeers().add(message.getSenderId());
//                    } else { //IDK what this else means
//                        Chunk c = new Chunk(message.getFileId(), message.getChunkNumber(), message.getReplicationDegree());
//                        peer.getChunks().put(key, c);
//                    }
//
//                } else if (message.getType() == MessageType.GETCHUNK) {
//                    String key = message.getFileId() + "-" + message.getChunkNumber();
//                    if (peer.getChunks().containsKey(key)) {
//                        String[] msgArgs = {peer.getProtocolVersion(),
//                                String.valueOf(peer.getId()),
//                                message.getFileId(),
//                                String.valueOf(message.getChunkNumber())};
//
//                        //TODO: refactor
//                        byte[] body;
//                        try (FileInputStream file = new FileInputStream(key)) {
//                            body = file.readAllBytes();
//                        }
//
//                        //Schedule the CHUNK message
//                        Message msgToSend = new Message(MessageType.CHUNK, msgArgs, body);
//                        ScheduledFuture<?> task = peer.getPool().schedule(new ChunkMessageSender(peer, msgToSend), new Random().nextInt(400), TimeUnit.MILLISECONDS);
//                        peer.getMessagesToSend().put(key, task);
//                    }
//                } else if (message.getType() == MessageType.DELETE) {
//                    peer.getChunks().forEach((key, value) -> {
//                        if (key.startsWith(message.getFileId()) && peer.getChunks().remove(key, value)) {
//                            File chunkToDelete = new File(key);
//                            chunkToDelete.delete();
//                        }
//                    });
//                } else if (message.getType() == MessageType.REMOVED) {
//                    String key = message.getFileId() + "-" + message.getChunkNumber();
//                    if (peer.getChunks().containsKey(key)) {
//                        peer.getChunks().get(key).getPeers().remove(message.getSenderId());
//
//
//                        //TODO: check if replication degree drops below desired (Kinda done)
//                        if (peer.getChunks().get(key).getPerceivedReplicationDegree() < peer.getChunks().get(key).getDesiredReplicationDegree()) {
//                            String[] msgArgs = {peer.getProtocolVersion(),
//                                    String.valueOf(peer.getId()),
//                                    message.getFileId(),
//                                    String.valueOf(message.getChunkNumber())};
//                            Message msgToSend = new Message(MessageType.PUTCHUNK, msgArgs, null);
//                            ScheduledFuture<?> task = peer.getPool().schedule(
//                                    new PutChunkMessageSender(peer, msgToSend, peer.getChunks().get(key).getDesiredReplicationDegree(), 5),
//                                    new Random().nextInt(400), TimeUnit.MILLISECONDS);
//                            peer.getBackupsToSend().put(key, task);
//                        }
//                    }
//                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
