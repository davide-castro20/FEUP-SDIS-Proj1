package g03.Messages;

import g03.Enchancements.Enhancements;
import g03.Peer;

import java.util.Arrays;

public class Message {

    String protocolVersion;
    MessageType type;
    int senderId;
    String fileId;
    int chunkNumber = -1;
    int replicationDegree = -1;
    byte[] body;
    int port = -1;

    public Message(byte[] packet) {
        String packetStr = new String(packet);
        String[] message = packetStr.split("\r\n\r\n");
        String[] header = message[0].split(" ");

        this.protocolVersion = header[0];
        try {
            this.type = MessageType.valueOf(header[1]);
        } catch (IllegalArgumentException e) {
            this.type = MessageType.UNKNOWN;
        }
        this.senderId = Integer.parseInt(header[2]);
        this.fileId = header[3];

        if(this.type != MessageType.DELETE && this.type != MessageType.DELETED)
            this.chunkNumber = Integer.parseInt(header[4]);
        if(this.type == MessageType.PUTCHUNK)
            this.replicationDegree = Integer.parseInt(header[5]);

        int indexBody = packetStr.indexOf("\r\n\r\n");
        if(indexBody + 4 >= packet.length) {
            if(type == MessageType.PUTCHUNK || type == MessageType.CHUNK)
                this.body = new byte[0];
            else
                this.body = null;
        }
        else
            this.body = Arrays.copyOfRange(packet, indexBody + 4, packet.length);

        if(Peer.supportsEnhancement(protocolVersion, Enhancements.RESTORE) && type == MessageType.GETCHUNK) {
            this.port = Integer.parseInt(message[0].split("\r\n")[1].trim());
        }
    }

    public Message(MessageType type, String[] args, byte[] body) {
        this.protocolVersion = args[0];
        this.type = type;
        this.senderId = Integer.parseInt(args[1]);
        this.fileId = args[2];
        this.body = body;

        if(type == MessageType.PUTCHUNK) {
            this.chunkNumber = Integer.parseInt(args[3]);
            this.replicationDegree = Integer.parseInt(args[4]);

        } else if(type != MessageType.DELETE && type != MessageType.DELETED) {
            this.chunkNumber = Integer.parseInt(args[3]);

            if(Peer.supportsEnhancement(protocolVersion, Enhancements.RESTORE) && this.type == MessageType.GETCHUNK) {
                this.port = Integer.parseInt(args[4]);
            }
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

        if(this.type == MessageType.GETCHUNK && this.port != -1) {
            header += "\r\n " + this.port;
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

    public int getPort() {
        return port;
    }
}
