package g03.ChannelRunnables;

import g03.Message;
import g03.MessageType;
import g03.Peer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MDR implements Runnable {

    private final Peer peer;

    public MDR(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message m = new Message(peer.getMDR().receive());
                if (m.getSenderId() != peer.getId() && m.getType() == MessageType.CHUNK) {
                    //TODO: maybe refactor this
                    new Thread(() -> {
                        try {
                            if (peer.getChunksToRestore().containsKey(m.getFileId())) {
                                if (peer.getChunksToRestore().get(m.getFileId()).contains(m.getChunkNumber())) {
                                    //TODO: usar nio?
                                    try (FileOutputStream out = new FileOutputStream(m.getFileId() + "-" + m.getChunkNumber())) {
                                        out.write(m.getBody());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    peer.getChunksToRestore().get(m.getFileId()).remove(m.getChunkNumber());
                                    if (peer.getChunksToRestore().get(m.getFileId()).size() == 0) {
                                        try (FileOutputStream out = new FileOutputStream(peer.getFiles().get(m.getFileId()).getPath() + "-restored")) {
                                            for (int i = 0; i < peer.getFiles().get(m.getFileId()).getChunkAmount(); i++) {
                                                try (FileInputStream in = new FileInputStream(m.getFileId() + "-" + i)) {
                                                    in.transferTo(out);
                                                }
                                            }

                                        }
                                        peer.getChunksToRestore().remove(m.getFileId());
                                    }
                                }

                            } else {
                                //To not send repeated Chunk messages
                                String key = m.getFileId() + "-" + m.getChunkNumber();
                                if (peer.getMessagesToSend().containsKey(key)) {
                                    peer.getMessagesToSend().get(key).cancel(false);
                                    peer.getMessagesToSend().remove(key);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
