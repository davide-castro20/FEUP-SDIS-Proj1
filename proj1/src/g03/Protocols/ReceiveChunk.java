package g03.Protocols;

import g03.Chunk;
import g03.Message;
import g03.MessageType;
import g03.Peer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ReceiveChunk implements Runnable {
    private final Peer peer;
    private final Message message;

    public ReceiveChunk(Peer peer, Message message) {
        this.peer = peer;
        this.message = message;
    }


    @Override
    public void run() {

        try (FileOutputStream out = new FileOutputStream(message.getFileId() + "-" + message.getChunkNumber())) {
            out.write(message.getBody());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Chunk c = new Chunk(message.getFileId(), message.getChunkNumber(), message.getReplicationDegree());

        this.peer.getChunks().put(message.getFileId() + "-" + message.getChunkNumber(), c);

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
