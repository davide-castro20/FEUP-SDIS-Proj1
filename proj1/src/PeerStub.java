import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PeerStub extends Remote {

    void backup(String path, int replicationDegree) throws RemoteException;
    void restore(String path) throws RemoteException;
    void delete(String path) throws RemoteException;
    void reclaim(long amountOfBytes) throws RemoteException;
    void state() throws RemoteException; //TODO: Change void return to something like a State Class
}