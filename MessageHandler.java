import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

public class MessageHandler implements Runnable {
    private Socket socket;
    private Tracker tracker;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    public MessageHandler(Socket socket, Tracker tracker) {
        this.socket = socket;
        this.tracker = tracker;

        try {
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(0);
        }
    }

    public void run() {
        String message;
        while (true) {
            try {
                if (socket.isClosed()) break;
                if (input.available() == 0) continue;
                
                // Wait for message
                message = input.readUTF();
                switch (message) {
                    case "register":
                        handleRegister();
                        break;

                    case "login":
                        handleLogin();
                        break;

                    case "logout":
                        handleLogout();
                        break;

                    case "notify":
                        handleNotify();
                        break;

                    default:
                        System.out.println("No such function: " + message +".");
                }

            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    private void handleRegister() {
        try {
            // Send true
            output.writeBoolean(true);
            output.flush();

            // Wait for credentials
            Object response = input.readObject();
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, String> credentials = (ConcurrentHashMap<String, String>) response;

            // Create an account and send success message
            boolean success = tracker.addSavedPeer(credentials.get("username"), credentials.get("password"));
            if (success)
                System.out.println("Tracker: Created user with username " + credentials.get("username") + ".");
            output.writeBoolean(success);
            output.flush();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void handleLogin() {
        try {
            // Send true
            output.writeBoolean(true);
            output.flush();

            // Wait for username and password
            Object response = input.readObject();
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, String> credentials = (ConcurrentHashMap<String, String>) response;
            int tokenId = tracker.loginPeer(credentials.get("username"), 
                                            credentials.get("password"), 
                                            socket);

            // Respond
            output.writeInt(tokenId);
            output.flush();

            // Wait for inform
            response = input.readObject();
            @SuppressWarnings("unchecked")
            List<String> files = (List<String>) response;
            tracker.addPeerFiles(credentials.get("username"), files);

            // Send true
            output.writeBoolean(true);
            output.flush();

            
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void handleLogout() {
        try {
            // Send true
            output.writeBoolean(true);
            output.flush();

            // Wait for token_id
            int token = input.readInt();
            for (int t : tracker.getTokens()) {
                if (token == t) {
                    // Logout the peer and send true
                    tracker.logoutPeer(token);
                    output.writeBoolean(true);
                    output.flush();
                    System.out.println("Tracker: Logged peer " + token + " out.");
                    return;
                }
            }

            // Send false(token_id doesn't match any logged in peer)
            output.writeBoolean(false);
            output.flush();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void handleNotify() {
        try {
            // Get token_id
            String token = input.readUTF();
            String username = tracker.getUsername(token);

            // Get filenames
            ConcurrentHashMap<String, List<String>> peerFiles = tracker.getPeerFiles();
            peerFiles.putIfAbsent(username, new ArrayList<String>());
            boolean received = false;
            String filename;
            while (input.available() > 0 || !received) {
                filename = input.readUTF();
                if (!peerFiles.get(username).contains(filename))
                    peerFiles.get(username).add(filename);
                   
                received = true;
            }

            // Send success message
            if (received) {
                tracker.setPeerFiles(peerFiles);
                System.out.println("Tracker: Got filenames from peer " + token + ".");
            } else {
                System.out.println("Tracker: Failed to receive files from peer " + token + ".");
            }
            
            output.writeBoolean(received);
            output.flush();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}