package g03;

import g03.ChannelRunnables.MC;
import g03.ChannelRunnables.MDB;
import g03.ChannelRunnables.MDR;
import g03.Protocols.*;

import java.io.*;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.*;

public class Peer implements PeerStub {

    int id;
    String protocolVersion;
    String serviceAccessPointName;

    Channel MCChannel;
    Channel MDBChannel;
    Channel MDRChannel;

    ConcurrentMap<String, Chunk> storedChunks;
    ConcurrentMap<String, FileInfo> files; // FilePath -> FileInfo

    ConcurrentMap<String, ScheduledFuture<?>> messagesToSend;
    ConcurrentMap<String, ScheduledFuture<?>> backupsToSend; //FOR THE RECLAIM PROTOCOL
    ConcurrentMap<String, List<Integer>> chunksToRestore;

    ScheduledExecutorService pool;
    ScheduledExecutorService synchronizer;

    long maxSpace = 100000;

    public static void main(String[] args) throws IOException, AlreadyBoundException {
        if (args.length != 6) {
            System.out.println("Usage: java Peer <protocol_version> <peer_id> <service_access_point> <MC_address>:<MC_Port> <MDB_address>:<MDB_Port> <MDR_address>:<MDR_Port>");
        }

        String protocolVersion = args[0];
        int peerId = Integer.parseInt(args[1]);
        String serviceAccessPointName = args[2];

        String[] MCinfo = args[3].split(":");
        Channel MCchannel = new Channel(MCinfo[0], Integer.parseInt(MCinfo[1]));

        String[] MDBinfo = args[4].split(":");
        Channel MDBchannel = new Channel(MDBinfo[0], Integer.parseInt(MDBinfo[1]));

        String[] MDRinfo = args[5].split(":");
        Channel MDRchannel = new Channel(MDRinfo[0], Integer.parseInt(MDRinfo[1]));

        Peer peer = new Peer(peerId, protocolVersion, serviceAccessPointName, MCchannel, MDBchannel, MDRchannel);
        peer.bindRMI();

        peer.synchronizer.scheduleAtFixedRate(new Synchronizer(peer), 0, 30, TimeUnit.SECONDS);

        new Thread(new MC(peer)).start();
        new Thread(new MDR(peer)).start();
        new Thread(new MDB(peer)).start();

    }

    public Peer(int id, String protocolVersion, String serviceAccessPointName, Channel MCChannel, Channel MDBChannel, Channel MDRChannel) {
        this.id = id;
        this.protocolVersion = protocolVersion;
        this.serviceAccessPointName = serviceAccessPointName;

        this.MCChannel = MCChannel;
        this.MDBChannel = MDBChannel;
        this.MDRChannel = MDRChannel;

        this.storedChunks = new ConcurrentHashMap<>();
        this.files = new ConcurrentHashMap<>();
        this.messagesToSend = new ConcurrentHashMap<>();
        this.chunksToRestore = new ConcurrentHashMap<>();

        this.pool = Executors.newScheduledThreadPool(16);
        this.synchronizer = Executors.newSingleThreadScheduledExecutor();

        this.readChunkFileData();
    }

    private void readChunkFileData() {
        try (FileInputStream fileInChunks = new FileInputStream("chunkData");
             ObjectInputStream chunksIn = new ObjectInputStream(fileInChunks)) {
            this.storedChunks = (ConcurrentMap<String, Chunk>) chunksIn.readObject();
        } catch (FileNotFoundException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        try (FileInputStream fileInFile = new FileInputStream("fileData");
             ObjectInputStream filesIn = new ObjectInputStream(fileInFile)) {
            this.files = (ConcurrentMap<String, FileInfo>) filesIn.readObject();
        } catch (FileNotFoundException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void bindRMI() throws RemoteException, AlreadyBoundException {
        PeerStub stub = (PeerStub) UnicastRemoteObject.exportObject(this, 0);

        // Bind the remote object's stub in the registry
        Registry registry = LocateRegistry.getRegistry();
        registry.bind(this.serviceAccessPointName, stub);
    }

    public void receive(Message message) throws IOException {
        ReceiveChunk receiveRun = new ReceiveChunk(this, message);
        pool.execute(receiveRun);
    }

    @Override
    public void backup(String path, int replicationDegree) throws RemoteException {
        Backup backupRun = new Backup(this, path, replicationDegree);
        pool.execute(backupRun);
    }

    @Override
    public void restore(String path) throws RemoteException {
        Restore restoreRun = new Restore(this, path);
        pool.execute(restoreRun);
    }

    @Override
    public void delete(String path) throws RemoteException {
        Delete deleteRun = new Delete(this, path);
        pool.execute(deleteRun);
    }

    @Override
    public void reclaim(long amountOfBytes) throws RemoteException {
        Reclaim reclaimRun = new Reclaim(this, amountOfBytes);
        pool.execute(reclaimRun);
    }

    @Override
    public void state() throws RemoteException {

    }

    public static String getFileIdString(String path) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] hash = digest.digest(path.getBytes());

        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit((b & 0xF), 16));
        }


        return result.toString();
    }

    public int getId() {
        return id;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public Channel getMDB() throws IOException {
        return MDBChannel;
    }

    public Channel getMC() throws IOException {
        return MCChannel;
    }

    public Channel getMDR() {
        return MDRChannel;
    }

    public ConcurrentMap<String, Chunk> getChunks() {
        return storedChunks;
    }

    public ConcurrentMap<String, FileInfo> getFiles() {
        return files;
    }

    public ConcurrentMap<String, ScheduledFuture<?>> getMessagesToSend() {
        return messagesToSend;
    }

    public ScheduledExecutorService getPool() {
        return pool;
    }

    public ConcurrentMap<String, List<Integer>> getChunksToRestore() {
        return chunksToRestore;
    }

    public double getCurrentSpace() {
        double currentSpace = 0; //in bytes

        for(Chunk chunk : storedChunks.values()) {
            currentSpace += new File(chunk.getFileId() + "-" + chunk.getChunkNumber()).length();
        }

        return currentSpace/1000.0; //returns in kbytes
    }

    public ConcurrentMap<String, ScheduledFuture<?>> getBackupsToSend() {
        return backupsToSend;
    }
}




























