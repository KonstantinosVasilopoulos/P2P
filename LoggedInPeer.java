import java.net.Socket;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

public class LoggedInPeer {
    private int tokenId;
    private String ipAddress;
    private int port;
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private SavedPeer user;
    
    public LoggedInPeer(int tokenId, int port, Socket socket, ObjectOutputStream output, 
                        ObjectInputStream input, SavedPeer user) {
        this.tokenId = tokenId;
        this.ipAddress = socket.getInetAddress().getHostAddress();
        this.port = port;
        this.socket = socket;
        this.output = output;
        this.input = input;
        this.user = user;
    }

    public int getTokenId() {
        return tokenId;
    }

    public Socket getSocket() {
        return socket;
    }

    public ObjectOutputStream getOutputStream() {
        return output;
    }

    public ObjectInputStream getInputStream() {
        return input;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public SavedPeer getUser() {
        return user;
    }
}