package g03;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class TCPInitiator implements Runnable {
    private Peer peer;
    private String fileHash;
    private int chunkNumber;
    private int port;

    public TCPInitiator(Peer peer, String fileHash, int chunkNumber) {
        this.peer = peer;
        this.fileHash = fileHash;
        this.chunkNumber = chunkNumber;
        this.port = peer.getTcp_ports().remove();
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port);
             Socket clientSocket = serverSocket.accept()) {

            peer.getChunksToRestore().get(fileHash).remove(Integer.valueOf(chunkNumber));

            BufferedInputStream in = new BufferedInputStream(clientSocket.getInputStream());

            byte[] readData = new byte[64000];
            int nRead;

            FileOutputStream outChunk = new FileOutputStream("backup/" + fileHash + "-" + chunkNumber);
            while ((nRead = in.read(readData, 0, 64000)) > 0) {
                byte[] toWrite = Arrays.copyOf(readData, nRead);
                outChunk.write(toWrite);
            }
            in.close();
            outChunk.close();

            if (peer.getChunksToRestore().containsKey(fileHash) && peer.getChunksToRestore().get(fileHash).size() == 0) {
                peer.getChunksToRestore().remove(fileHash);

                System.out.println("ASSEMBLING FILE");
                FileInfo fileInfo = peer.getFiles().values().stream().filter(f -> f.getHash().equals(fileHash)).findFirst().get();

                System.out.println(fileInfo.getPath() + "-restored");
                try (FileOutputStream out = new FileOutputStream(fileInfo.getPath() + "-restored")) {
                    for (int i = 0; i < fileInfo.getChunkAmount(); i++) {
                        System.out.println(fileHash + "-" + i);
                        try (FileInputStream inChunk = new FileInputStream("backup/" + fileHash + "-" + i)) {
                            inChunk.transferTo(out);
                            inChunk.close();
                            File chunk = new File("backup/" + fileHash + "-" + i);
                            chunk.delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
