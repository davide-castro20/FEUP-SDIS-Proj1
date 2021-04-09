package g03.Channels;

import g03.*;
import g03.Enchancements.Enhancements;
import g03.Messages.Message;
import g03.Messages.MessageType;

import java.io.*;

public class MDR implements Runnable {

    private final Peer peer;

    public MDR(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        while (true) {
            try {
                byte[] packet = peer.getMDR().receive();
                if(packet == null)
                    continue;
                Message m = new Message(packet);

                if(Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.DELETE))
                    peer.checkDeleted(m);

                if (m.getSenderId() != peer.getId() && m.getType() == MessageType.CHUNK
                        && (!Peer.supportsEnhancement(m.getProtocolVersion(), Enhancements.RESTORE) || !Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.RESTORE))) {
                    //TODO: maybe refactor this
                    Runnable run;
                    run = () -> {
                        try {
                            System.out.println("CHUNK NUMBER: " + m.getChunkNumber());
                            if (peer.getChunksToRestore().containsKey(m.getFileId())) {
                                if (peer.getChunksToRestore().get(m.getFileId()).contains(m.getChunkNumber())) {
                                    if(Peer.supportsEnhancement(peer.getProtocolVersion(), Enhancements.RESTORE)) {
                                        //cancel tcp accept wait
                                        peer.getTcpConnections().get(m.getFileId() + "-" + m.getChunkNumber()).cancel(true);
                                    }
                                    //TODO: usar nio?
                                    try (FileOutputStream out = new FileOutputStream("restore/" + m.getFileId() + "-" + m.getChunkNumber())) {
                                        out.write(m.getBody());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    peer.getChunksToRestore().get(m.getFileId()).remove(Integer.valueOf(m.getChunkNumber()));
                                    if (peer.getChunksToRestore().containsKey(m.getFileId()) && peer.getChunksToRestore().get(m.getFileId()).size() == 0) {
                                        peer.getChunksToRestore().remove(m.getFileId());

                                        System.out.println("ASSEMBLING FILE");
                                        FileInfo fileInfo = peer.getFiles().values().stream().filter(f -> f.getHash().equals(m.getFileId())).findFirst().get();

                                        System.out.println(fileInfo.getPath() + "-restored");
                                        try (FileOutputStream out = new FileOutputStream(fileInfo.getPath() + "-restored")) {
                                            for (int i = 0; i < fileInfo.getChunkAmount(); i++) {
                                                System.out.println(m.getFileId() + "-" + i);
                                                try (FileInputStream in = new FileInputStream("restore/" + m.getFileId() + "-" + i)) {
                                                    in.transferTo(out);
                                                    in.close();
                                                    File chunk = new File("restore/" + m.getFileId() + "-" + i);
                                                    chunk.delete();
                                                }
                                            }

                                        }
                                        peer.getOngoing().remove("restore-" + fileInfo.getPath());
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
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    };
                    peer.getPool().execute(run);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
