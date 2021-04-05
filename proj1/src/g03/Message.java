package g03;

import java.util.Arrays;

public class Message {

    String protocolVersion;
    MessageType type;
    int senderId;
    String fileId;
    int chunkNumber = -1;
    int replicationDegree = -1;
    byte[] body;

    public Message(byte[] packet) {
        String packetStr = new String(packet);
        String[] message = packetStr.split("\r\n\r\n");
        String[] header = message[0].split(" ");

        this.protocolVersion = header[0];
        this.type = MessageType.valueOf(header[1]);
        this.senderId = Integer.parseInt(header[2]);
        this.fileId = header[3];
        this.chunkNumber = Integer.parseInt(header[4]);
        if(this.type == MessageType.PUTCHUNK)
            this.replicationDegree = Integer.parseInt(header[5]);

        int indexBody = packetStr.indexOf("\r\n\r\n");
        this.body = Arrays.copyOfRange(packet, indexBody + 4, packet.length);
    }

    public Message(MessageType type, String[] args, byte[] body) {
        this.protocolVersion = args[0];
        this.type = type;
        this.senderId = Integer.parseInt(args[1]);
        this.fileId = args[2];
        this.body = body;

        switch (type) {
            case PUTCHUNK -> {
                this.chunkNumber = Integer.parseInt(args[3]);
                this.replicationDegree = Integer.parseInt(args[4]);
            }
            case STORED, CHUNK, GETCHUNK, REMOVED -> this.chunkNumber = Integer.parseInt(args[3]);
        }
    }

    public byte[] toByteArray() {
        String header = this.protocolVersion +
                " " +
                this.type.name() +
                " " +
                this.senderId + //PeerId
                " " +
                this.fileId + //FileId
                " ";

        if (this.chunkNumber != -1) {
            header += this.chunkNumber + " ";
        }
        if (this.replicationDegree != -1) {
            header += this.replicationDegree + " ";
        }

        header += " \r\n\r\n";

        if (this.body != null) {
            byte[] toSend = new byte[header.length() + this.body.length];
            System.arraycopy(header.getBytes(), 0, toSend, 0, header.length());
            System.arraycopy(this.body, 0, toSend, header.length(), this.body.length);
            return toSend;
        }
        return header.getBytes();

    }

    public byte[] getBody() {
        return body;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public MessageType getType() {
        return type;
    }

    public int getSenderId() {
        return senderId;
    }

    public String getFileId() {
        return fileId;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public int getReplicationDegree() {
        return replicationDegree;
    }
}
