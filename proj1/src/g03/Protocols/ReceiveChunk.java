package g03.Protocols;

import g03.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ReceiveChunk implements Runnable {
    private final Peer peer;
    private final Message message;

    public ReceiveChunk(Peer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }


    @Override
    public void run() {
        String key = message.getFileId() + "-" + message.getChunkNumber();



        if(!peer.getChunks().containsKey(key)) {

            Stream<FileInfo> fileInThisPeer = peer.getFiles().values().stream().filter(f -> f.getHash().equals(message.getFileId()));
            if(fileInThisPeer.count() > 0) {
                return;
            }

            // will store if there is enough space in the peer
            if(peer.getRemainingSpace() >= message.getBody().length) {
                peer.addSpace(message.getBody().length);

                if(message.getBody().length > 0) {
                    try (FileOutputStream out = new FileOutputStream("backup/" + key)) {
                        out.write(message.getBody());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    File file = new File(key);
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                Chunk c = new Chunk(message.getFileId(), message.getChunkNumber(), message.getReplicationDegree(), message.getBody().length);
                c.addPeer(peer.getId()); //set itself as peer

                this.peer.getChunks().put(key, c);

                this.sendStoredMessage();
            }
        } else { //if the peer already has the chunk
            this.sendStoredMessage();
        }

        // in case this peer is trying to backup this chunk (reclaim - the replication drops) that operation will be canceled
        // because another peer already sent it
        if(peer.getBackupsToSend().containsKey(key)) {
            peer.getBackupsToSend().get(key).cancel(false);
            peer.getBackupsToSend().remove(key);
        }
    }

    private void sendStoredMessage() {
        Message reply = new Message(MessageType.STORED,
                new String[]{
                        String.valueOf(this.peer.getProtocolVersion()),
                        String.valueOf(this.peer.getId()), message.getFileId(),
                        String.valueOf(message.getChunkNumber())},
                null);
    
        //Refactor
        peer.getPool().schedule(() -> {
            try {
                this.peer.getMC().send(reply);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, new Random().nextInt(400), TimeUnit.MILLISECONDS);
    }
}
