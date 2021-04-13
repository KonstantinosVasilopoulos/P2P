import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

public class Peer {
    private ServerSocket server;
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    // Peer username and password
    private String username;
    private String password;

    public Peer(String username, String password) {
        this.username = username;
        this.password = password;
        
        start();
    }

    // TODO: Add a String planFilename to load commands
    private void start() {
        boolean success = register();

        if (success)
            System.out.println("Peer: Registered with username " + username + ".");
        else
        System.out.println("Peer: Unable to register with username " + username + ".");
    }

    private boolean register() {
        try {
            // Connect to the tracker
            socket = new Socket("127.0.0.1", 55217);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());

            // Send "register" message
            output.writeUTF("register");
            output.flush();

            // Wait for true
            boolean response = input.readBoolean(); 
            if (response) {
                // Send username and password
                ConcurrentHashMap<String, String> credentials = new ConcurrentHashMap<>();
                credentials.put("username", username);
                credentials.put("password", password);
                output.writeObject(credentials);
                output.flush();

                System.out.println("Peer: Registering...");
            } else {
                return false;
            }

            // Wait for response message
            response = input.readBoolean();

            // Close sockets and streams
            output.close();
            input.close();
            socket.close();

            return response;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        Peer peerOne = new Peer(args[0], args[1]);
    }
}