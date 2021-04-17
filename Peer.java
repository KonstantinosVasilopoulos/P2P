import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;

public class Peer {
    private ServerSocket server;
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    // Peer username and password
    private ConcurrentHashMap<String, String> credentials;

    public Peer(String username, String password) {
        credentials = new ConcurrentHashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);

        // Create a shared_directory if one doesn't exist
        new File("shared_directory/" + username + "/").mkdirs();
        
        start();
    }

    // TODO: Add a String planFilename to load commands
    private void start() {
        boolean success = register();
        if (success)
            success = login();
            if (success)
                logout();
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
                output.writeObject(credentials);
                output.flush();

                System.out.println("Peer: Registering...");
            } else {
                System.out.println("Peer: Unable to register with username " + credentials.get("username") + ".");
                return false;
            }

            // Wait for response message
            response = input.readBoolean();

            // Close sockets and streams
            output.close();
            input.close();
            socket.close();

            if (response)
                System.out.println("Peer: Registered with username " + credentials.get("username") + ".");
            else
                System.out.println("Peer: Unable to register with username " + credentials.get("username") + ".");

            return response;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    private boolean login() {
        try {
            // Connect to the tracker
            socket = new Socket("127.0.0.1", 55217);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());

            // Send "login"
            output.writeUTF("login");
            output.flush();

            // Wait for true
            boolean response = input.readBoolean();
            if (response) {
                // Send username and password
                output.writeObject(credentials);
                output.flush();
                
            } else {
                System.out.println("Peer: Unable to login.");
                return false;
            }

            // Wait for response
            int tokeId = input.readInt();
            if (tokeId == 0) {
                System.out.println("Peer: Unable to login.");
                return false;
            } else {
                System.out.println("Peer " + tokeId +": Logged in.");
                credentials.put("token_id", String.valueOf(tokeId));
            }

            // Send inform
            List<String> files = new ArrayList<>();
            for (File file : new File("shared_directory/" + credentials.get("username") + "/").listFiles())
                files.add(file.getName());

            output.writeObject(files);
            output.flush();

            output.close();
            input.close();
            socket.close();
            return true;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    private boolean logout() {
        try {
            // Connect to the tracker
            socket = new Socket("127.0.0.1", 55217);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());

            // Send "logout"
            output.writeUTF("logout");
            output.flush();

            // Wait for true
            boolean response = input.readBoolean();
            if (!response) {
                System.out.println("Peer " + credentials.get("token_id") + ": Unable to logout.");
                return false;
            }

            // Send token_id
            output.writeInt(Integer.parseInt(credentials.get("token_id")));
            output.flush();

            // Wait for response
            response = input.readBoolean();
            if (response)
                System.out.println("Peer: Logout successful.");
            else
                System.out.println("Peer: Unable to logout.");

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
        new Peer(args[0], args[1]);
    }
}