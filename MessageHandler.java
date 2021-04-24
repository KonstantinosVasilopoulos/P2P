import java.net.Socket;
import java.util.List;
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
                if (message.equals("register")) {
                    handleRegister();
                } else if (message.equals("login")) {
                    handleLogin();
                } else if (message.equals("logout")) {
                    System.out.println("logout");
                    handleLogout();
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
            output.writeBoolean(success);
            output.flush();
            if (success)
                System.out.println("Tracker: Created user with username " + credentials.get("username") + ".");

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
}