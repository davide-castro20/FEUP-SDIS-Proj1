package g03;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class TCPInitiator implements Runnable {
    private Peer peer;
    private String fileHash;
    private int chunkNumber;
    private int port;

    public TCPInitiator(Peer peer, String fileHash, int chunkNumber, int port) {
        this.peer = peer;
        this.fileHash = fileHash;
        this.chunkNumber = chunkNumber;
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(2000);
            Socket clientSocket = serverSocket.accept();

            System.out.println("CHUNK NUMBER: " + chunkNumber + " TCP");
            peer.getChunksToRestore().get(fileHash).remove(Integer.valueOf(chunkNumber));

            BufferedInputStream in = new BufferedInputStream(clientSocket.getInputStream());

            byte[] readData = new byte[64000];
            int nRead;

            FileOutputStream outChunk = new FileOutputStream("restore/" + fileHash + "-" + chunkNumber);
            while ((nRead = in.read(readData, 0, 64000)) > 0) {
                byte[] toWrite = Arrays.copyOf(readData, nRead);
                outChunk.write(toWrite);
            }
            in.close();
            outChunk.close();
            clientSocket.close();

            if (peer.getChunksToRestore().containsKey(fileHash) && peer.getChunksToRestore().get(fileHash).size() == 0) {
                peer.getChunksToRestore().remove(fileHash);

                System.out.println("ASSEMBLING FILE");
                FileInfo fileInfo = peer.getFiles().values().stream().filter(f -> f.getHash().equals(fileHash)).findFirst().get();

                System.out.println(fileInfo.getPath() + "-restored");
                try (FileOutputStream out = new FileOutputStream(fileInfo.getPath() + "-restored")) {
                    for (int i = 0; i < fileInfo.getChunkAmount(); i++) {
                        System.out.println(fileHash + "-" + i);
                        try (FileInputStream inChunk = new FileInputStream("restore/" + fileHash + "-" + i)) {
                            inChunk.transferTo(out);
                            inChunk.close();
                            File chunk = new File("restore/" + fileHash + "-" + i);
                            chunk.delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                }
            }

        } catch(SocketTimeoutException ignored) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
