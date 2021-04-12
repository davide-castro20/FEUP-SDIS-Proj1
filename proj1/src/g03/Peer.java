package g03;

import g03.Channels.Channel;
import g03.Channels.MC;
import g03.Channels.MDB;
import g03.Channels.MDR;
import g03.Enchancements.Enhancements;
import g03.Messages.Message;
import g03.Messages.MessageType;
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

    // FOR BACKUP ENHANCEMENT
    Thread MDBthread;
    boolean stoppedMDB;

    ConcurrentMap<String, Chunk> storedChunks;
    ConcurrentMap<String, FileInfo> files; // FileHash -> FileInfo
    ConcurrentMap<String, ScheduledFuture<?>> messagesToSend;
    ConcurrentMap<String, ScheduledFuture<?>> backupsToSend; //FOR THE RECLAIM PROTOCOL
    ConcurrentMap<String, List<Integer>> chunksToRestore;

    Set<String> ongoing;

    //Used for the RESTORE Enhancement
    ConcurrentMap<String, ScheduledFuture<?>> tcpConnections;

    //Used for the DELETE Enhancement
    ConcurrentMap<String, Set<Integer>> peersDidNotDeleteFiles; //

    ConcurrentLinkedQueue<Integer> tcp_ports;
    ScheduledExecutorService backupPool;
    ScheduledExecutorService restorePool;
    ScheduledExecutorService reclaimPool;
    ScheduledExecutorService deletePool;
    ScheduledExecutorService synchronizer;

    long maxSpace = 100000000000L; // bytes
    long currentSpace = 0; // bytes

    public static void main(String[] args) throws IOException, AlreadyBoundException {
        if (args.length != 9) {
            System.out.println("Usage: java Peer <protocol_version> <peer_id> <service_access_point> <MC_address> <MC_Port> <MDB_address> <MDB_Port> <MDR_address> <MDR_Port>");
            return;
        }

        String protocolVersion = args[0];
        if(!args[0].equals("1.0") && !args[0].equals("1.1") && !args[0].equals("1.2") && !args[0].equals("1.3") && !args[0].equals("1.4")) {
            System.out.println("Protocol versions available: 1.0, 1.1, 1.2, 1.3, 1.4");
            return;
        }

        int peerId = Integer.parseInt(args[1]);
        String serviceAccessPointName = args[2];

        Channel MCchannel = new Channel(args[3], Integer.parseInt(args[4]));

        Channel MDBchannel = new Channel(args[5], Integer.parseInt(args[6]));

        Channel MDRchannel = new Channel(args[7], Integer.parseInt(args[8]));

        Peer peer = new Peer(peerId, protocolVersion, serviceAccessPointName, MCchannel, MDBchannel, MDRchannel);

        peer.synchronizer.scheduleAtFixedRate(new Synchronizer(peer), 0, 1, TimeUnit.SECONDS);

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

        this.backupPool = Executors.newScheduledThreadPool(16);
        this.reclaimPool = Executors.newScheduledThreadPool(16);
        this.deletePool = Executors.newScheduledThreadPool(16);
        this.restorePool = Executors.newScheduledThreadPool(16);
        this.synchronizer = Executors.newSingleThreadScheduledExecutor();

        this.tcp_ports = IntStream.range(40000 + 100*(id-1), 40000 + 100*id).boxed()
                .collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
        this.tcpConnections = new ConcurrentHashMap<>();

        this.peersDidNotDeleteFiles = new ConcurrentHashMap<>();

        this.readState();

        this.bindRMI();

        new Thread(new MC(this)).start();
        new Thread(new MDR(this)).start();
        this.MDBthread = new Thread(new MDB(this));
        this.MDBthread.start();
        this.stoppedMDB = false;

        this.checkOperations();
    }


    private void readState() {
        try (FileInputStream stateIn = new FileInputStream("peerState");
             ObjectInputStream stateInObject = new ObjectInputStream(stateIn)) {
                PeerState peerState = (PeerState) stateInObject.readObject();
                currentSpace = peerState.currentSpace;
                maxSpace = peerState.maxSpace;
                storedChunks = (ConcurrentMap)peerState.storedChunks;
                files = (ConcurrentMap)peerState.files;
                peersDidNotDeleteFiles = (ConcurrentMap)peerState.peersDidNotDeleteFiles;
                ongoing = peerState.onGoingOperations;
        } catch (FileNotFoundException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void checkDeleted(Message message) {
        for(Map.Entry<String, Set<Integer>> deletedFiles : peersDidNotDeleteFiles.entrySet()) {
            if(deletedFiles.getValue().contains(message.getSenderId())) {

                String[] msgArgs = {protocolVersion,
                        String.valueOf(id),
                        deletedFiles.getKey()};
                Message deleteMsg = new Message(MessageType.DELETE, msgArgs, null);

                try {
                    MCChannel.send(deleteMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void checkOperations() {
        for(String op : ongoing) {
            String[] opElems = op.split("-");
            ongoing.remove(op);
            switch (opElems[0]) {
                case "backup":
                    this.backup(opElems[1], Integer.parseInt(opElems[2]));
                    break;
                case "restore":
                    this.restore(opElems[1]);
                    break;
                case "delete":
                    this.delete(opElems[1]);
                    break;
                case "reclaim":
                    this.reclaim(Long.parseLong(opElems[1]));
                    break;
                default:
                    break;
            }
        }
    }

    public void bindRMI() throws RemoteException {
        PeerStub stub = (PeerStub) UnicastRemoteObject.exportObject(this, 0);

        try {
            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(this.serviceAccessPointName, stub);
        } catch (Exception e) {
            System.err.println("Couldn't bind RMI");
        }
    }

    public void receive(Message message) {
        ReceiveChunk receiveRun = new ReceiveChunk(this, message);
        restorePool.execute(receiveRun);
    }

    @Override
    public void backup(String path, int replicationDegree) {
        String opName = "backup-" + path + "-" + replicationDegree;
        if(ongoing.contains(opName)) //if a similar operation is ongoing
            return;
        ongoing.add(opName);

        Backup backupRun = new Backup(this, path, replicationDegree);
        backupPool.execute(backupRun);
    }

    @Override
    public void restore(String path) {
        String opName = "restore-" + path;
        if(ongoing.contains(opName)) //if a similar operation is ongoing
            return;
        ongoing.add(opName);

        Restore restoreRun = new Restore(this, path);
        restorePool.execute(restoreRun);
    }

    @Override
    public void delete(String path) {
        String opName = "delete-" + path;
        if(ongoing.contains(opName)) //if a similar operation is ongoing
            return;
        ongoing.add(opName);

        Delete deleteRun = new Delete(this, path);
        deletePool.execute(deleteRun);
    }

    @Override
    public void reclaim(long amountOfKBytes) {
        String opName = "reclaim-" + (amountOfKBytes * 1000);
        if(ongoing.contains(opName)) //if a similar operation is ongoing
            return;
        ongoing.add(opName);

        Reclaim reclaimRun = new Reclaim(this, amountOfKBytes * 1000);
        reclaimPool.execute(reclaimRun);
    }

    @Override
    public PeerState state() throws RemoteException {
        return new PeerState(maxSpace, currentSpace, storedChunks, files, peersDidNotDeleteFiles, ongoing);
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

    public ScheduledExecutorService getBackupPool() {
        return backupPool;
    }

    public ScheduledExecutorService getReclaimPool() {
        return reclaimPool;
    }

    public ScheduledExecutorService getDeletePool() {
        return deletePool;
    }

    public ScheduledExecutorService getRestorePool() {
        return restorePool;
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
            case "1.4":
                return true;
            default:
                break;
        }

        return false;
    }

    public Thread getMDBthread() { return MDBthread; }

    public void interruptMDB() throws IOException {
        System.out.println("INTERRUPTING MDB");
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

    public Set<String> getOngoing() { return ongoing; }
}


