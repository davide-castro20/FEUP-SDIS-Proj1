package g03;

import g03.Protocols.*;

import java.io.*;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
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

    ExecutorService pool;
    ScheduledExecutorService synchronizer;


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

        Thread MCthread = new Thread(() -> {
            while (true) {
                try {
                    Message message = new Message(MCchannel.receive());
                    if (message.type == MessageType.STORED) {
                        String key = message.fileId + "-" + message.chunkNumber;

                        if(peer.storedChunks.containsKey(key)) {
                            Chunk c = peer.storedChunks.get(key);
                            c.getPeers().add(message.getSenderId());
                        }

                    } else if (message.type == MessageType.GETCHUNK) {
                        String key = message.fileId + "-" + message.chunkNumber;
                        if (peer.storedChunks.containsKey(key)) {
                            String[] msgArgs = {peer.protocolVersion,
                                    String.valueOf(peer.id),
                                    message.fileId,
                                    String.valueOf(message.chunkNumber)};

                            byte[] body = null;
                            try (FileInputStream file = new FileInputStream(key)) {
                                body = file.readAllBytes();
                            }

                            Message msgToSend = new Message(MessageType.CHUNK, msgArgs, body);
                            int timeToWait = new Random().nextInt(400);
                            Thread.sleep(timeToWait);
                            //TODO: falta parte de verificar se são recebidas chunk messages antes de enviar
                            // (usar a thread pool)

                            peer.MDRChannel.send(msgToSend);
                        }
                    } else if(message.type == MessageType.DELETE) {
                        peer.storedChunks.forEach((key, value) -> {
                            if(key.startsWith(message.fileId) && peer.storedChunks.remove(key, value)) {
                                File chunkToDelete = new File(key);
                                chunkToDelete.delete();
                            }
                        });
                    } else if(message.type == MessageType.REMOVED) {
                        String key = message.fileId + "-" + message.chunkNumber;
                        if(peer.storedChunks.containsKey(key)) {
                            peer.storedChunks.get(key).getPeers().remove(message.getSenderId());
                        }
                        //TODO: check if replication degree drops below desired
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }


        });

        Thread MDBthread = new Thread(() -> {
            while (true) {
                try {
                    Message m = new Message(MDBchannel.receive());
                    if (m.senderId != peer.id) {
                        peer.receive(m);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        });

        MCthread.start();
        MDBthread.start();

        System.out.println(Peer.getFileIdString("davinki.mp3"));
//        if (peer.id == 1) {
//            peer.backup("davinki.mp3", 1);
//        }

//        if (peer.id == 2) {
//            byte[] received;
//            while ((received = peer.MDBChannel.receive()) != null) {
//                byte[] finalReceived = received;
//                new Thread(() -> {
//                    try {
//                        peer.receive(finalReceived);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }).start();
//
//            }
//        }


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

        this.pool = Executors.newFixedThreadPool(16);
        this.synchronizer = Executors.newSingleThreadScheduledExecutor();

        this.readChunkFileData();
    }

    private void readChunkFileData() {
        try(FileInputStream fileInChunks = new FileInputStream("chunkData");
            ObjectInputStream chunksIn = new ObjectInputStream(fileInChunks) )
        {
            this.storedChunks = (ConcurrentMap<String, Chunk>) chunksIn.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        try(FileInputStream fileInFile = new FileInputStream("fileData");
            ObjectInputStream filesIn = new ObjectInputStream(fileInFile) )
        {
            this.files = (ConcurrentMap<String, FileInfo>) filesIn.readObject();

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
        Receive receiveRun = new Receive(this, message);
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
}




























