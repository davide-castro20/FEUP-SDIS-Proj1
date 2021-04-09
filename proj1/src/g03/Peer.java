package g03;

import g03.Channels.Channel;
import g03.Channels.MC;
import g03.Channels.MDB;
import g03.Channels.MDR;
import g03.Enchancements.Enhancements;
import g03.Messages.Message;
import g03.Protocols.*;

import java.io.*;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Peer implements PeerStub {

    int id;
    String protocolVersion;
    String serviceAccessPointName;

    Channel MCChannel;
    Channel MDBChannel;
    Channel MDRChannel;

    Thread MDBthread;
    boolean stoppedMDB;

    ConcurrentMap<String, Chunk> storedChunks;
    ConcurrentMap<String, FileInfo> files; // FileHash -> FileInfo
    ConcurrentMap<String, ScheduledFuture<?>> messagesToSend;
    ConcurrentMap<String, ScheduledFuture<?>> backupsToSend; //FOR THE RECLAIM PROTOCOL
    ConcurrentMap<String, List<Integer>> chunksToRestore;

    Set<Runnable> ongoing;

    //Used for the RESTORE Enhancement
    ConcurrentMap<String, ScheduledFuture<?>> tcpConnections;

    //Used for the DELETE Enhancement
    ConcurrentMap<String, Set<Integer>> peersDidNotDeleteFiles;

    ConcurrentLinkedQueue<Integer> tcp_ports;
    ScheduledExecutorService pool;
    ScheduledExecutorService synchronizer;

    long maxSpace = 100000000000L; // bytes
    long currentSpace = 0; // bytes

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

        peer.synchronizer.scheduleAtFixedRate(new Synchronizer(peer), 0, 2, TimeUnit.SECONDS);

    }

    public Peer(int id, String protocolVersion, String serviceAccessPointName, Channel MCChannel, Channel MDBChannel, Channel MDRChannel) throws AlreadyBoundException, RemoteException {
        this.id = id;
        this.protocolVersion = protocolVersion;
        this.serviceAccessPointName = serviceAccessPointName;

        this.MCChannel = MCChannel;
        this.MDBChannel = MDBChannel;
        this.MDRChannel = MDRChannel;

        this.storedChunks = new ConcurrentHashMap<>();
        this.files = new ConcurrentHashMap<>();
        this.messagesToSend = new ConcurrentHashMap<>();
        this.backupsToSend = new ConcurrentHashMap<>();
        this.chunksToRestore = new ConcurrentHashMap<>();

        this.ongoing = new HashSet<>();

        this.pool = Executors.newScheduledThreadPool(16);
        this.synchronizer = Executors.newSingleThreadScheduledExecutor();

        this.readState();

//        this.checkChunks();

        //TODO: Start with port 0
        this.tcp_ports = IntStream.range(40000 + 100*(id-1), 40000 + 100*id).boxed()
                .collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
        this.tcpConnections = new ConcurrentHashMap<>();

        this.peersDidNotDeleteFiles = new ConcurrentHashMap<>();

        this.bindRMI();

        new Thread(new MC(this)).start();
        new Thread(new MDR(this)).start();
        this.MDBthread = new Thread(new MDB(this));
        this.MDBthread.start();
        this.stoppedMDB = false;
    }


    private void readState() {
        try (FileInputStream stateIn = new FileInputStream("peerState");
             ObjectInputStream stateInObject = new ObjectInputStream(stateIn)) {
                PeerState peerState = (PeerState) stateInObject.readObject();
                currentSpace = peerState.currentSpace;
                maxSpace = peerState.maxSpace;
                storedChunks = (ConcurrentMap)peerState.storedChunks;
                files = (ConcurrentMap)peerState.files;

        } catch (FileNotFoundException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void checkChunks() {
        for(Map.Entry<String, Chunk> storedChunk : storedChunks.entrySet()) {

        }
    }

    public void bindRMI() throws RemoteException, AlreadyBoundException {
        PeerStub stub = (PeerStub) UnicastRemoteObject.exportObject(this, 0);

        // Bind the remote object's stub in the registry
        Registry registry = LocateRegistry.getRegistry();
        registry.bind(this.serviceAccessPointName, stub);
    }

    public void receive(Message message) {
        ReceiveChunk receiveRun = new ReceiveChunk(this, message);
        pool.execute(receiveRun);
    }

    @Override
    public void backup(String path, int replicationDegree) {
        Backup backupRun = new Backup(this, path, replicationDegree);
        pool.execute(backupRun);
    }

    @Override
    public void restore(String path) {
        Restore restoreRun = new Restore(this, path);
        pool.execute(restoreRun);
    }

    @Override
    public void delete(String path) {
        Delete deleteRun = new Delete(this, path);
        pool.execute(deleteRun);
    }

    @Override
    public void reclaim(long amountOfKBytes) {
        Reclaim reclaimRun = new Reclaim(this, amountOfKBytes * 1000);
        pool.execute(reclaimRun);
    }

    @Override
    public PeerState state() throws RemoteException {
        return new PeerState(maxSpace, currentSpace, storedChunks, files);
    }

    public static String getFileIdString(String path, int peerID) {
        MessageDigest digest = null;

        File file = new File(path);

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] hash = digest.digest((path + file.lastModified() + peerID).getBytes());

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

    public long getCurrentSpace() { return currentSpace; } // bytes

    public long addSpace(long space) {
        currentSpace += space;
        if(Peer.supportsEnhancement(protocolVersion, Enhancements.BACKUP)) {
            if(currentSpace == maxSpace && !stoppedMDB) {
                try {
                    this.interruptMDB();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.out.println(stoppedMDB);
        }
        return currentSpace;
    }

    public long removeSpace(long space) {
        currentSpace -= space;

        if(Peer.supportsEnhancement(protocolVersion, Enhancements.BACKUP)) {
            if (currentSpace < maxSpace && stoppedMDB) {
                try {
                    this.restartMDB();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.out.println(stoppedMDB);
        }

        return currentSpace;
    }

    public long getMaxSpace() { return maxSpace; } // in bytes

    public void setMaxSpace(long maxSpace) {
        this.maxSpace = maxSpace;

        if(Peer.supportsEnhancement(protocolVersion, Enhancements.BACKUP)) {
            try {
                if(currentSpace == maxSpace && !stoppedMDB)
                    this.interruptMDB();
                else if(currentSpace < maxSpace && stoppedMDB) {
                    this.restartMDB();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println(stoppedMDB);
        }
    }

    public double getRemainingSpace() { return maxSpace - currentSpace; } // in bytes

    public ConcurrentMap<String, ScheduledFuture<?>> getBackupsToSend() {
        return backupsToSend;
    }

    public ConcurrentLinkedQueue<Integer> getTcp_ports() {
        return tcp_ports;
    }

    public ConcurrentMap<String, ScheduledFuture<?>> getTcpConnections() {
        return tcpConnections;
    }

    public static boolean supportsEnhancement(String version, Enhancements enhancement) {
        switch (version) {
            case "1.1":
                return enhancement == Enhancements.RESTORE;
            case "1.2":
                return enhancement == Enhancements.BACKUP;
            case "1.3":
                return enhancement == Enhancements.DELETE;
            default:
                break;
        }

        return false;
    }

    public Thread getMDBthread() { return MDBthread; }

    public void interruptMDB() throws IOException {
        System.out.println("INTERRUPTING MDB");
//        MDBthread.interrupt();
        this.stoppedMDB = true;
        MDBChannel.leaveGroup();
    }
    public void restartMDB() throws IOException {
        System.out.println("RESTARTING MDB");
        MDBChannel.joinGroup();
        if(this.stoppedMDB) {
            stoppedMDB = false;
            MDBthread = new Thread(new MDB(this));
            MDBthread.start();
        }
    }

    public boolean getStoppedMDB() { return stoppedMDB; }

    public ConcurrentMap<String, Set<Integer>> getPeersDidNotDeleteFiles() {
        return peersDidNotDeleteFiles;
    }
}


