import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;
import java.io.IOException;

public class Tracker {
    private ServerSocket server;

    // Data
    private ExecutorService socketPool;
    private List<SavedPeer> savedPeers;
    private List<LoggedInPeer> loggedInPeers;
    private List<Integer> tokens;  // A list for holding all active token ids
    // This hashmap holds a peer's username with his files' names
    private ConcurrentHashMap<String, List<String>> peerFiles;

    public Tracker() {
        socketPool = Executors.newFixedThreadPool(10);
        savedPeers = new ArrayList<>();
        loggedInPeers = new ArrayList<>();
        tokens = new ArrayList<>();
        peerFiles = new ConcurrentHashMap<>();

        start();
    }

    private void start() {
        try {
            // Open server socket at port 55217
            server = new ServerSocket(55217);
            System.out.println("Tracker: Online and listening at port 55217.");

            while (true) {
                // Wait for clients
                Socket socket = server.accept();

                // The tracker uses threads to process each client individualy
                Runnable thread = new MessageHandler(socket, this);
                socketPool.execute(thread);
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
            socketPool.shutdown();
            System.exit(0);
        }
    }

    // Try to create a new peer. If the peer's username already exists return false.
    public boolean addSavedPeer(String username, String password) {
        for (SavedPeer peer : savedPeers) {
            if (peer.getUsername().equals(username))
                return false;
        }

        savedPeers.add(new SavedPeer(username, password));
        return true;
    }

    // Return the token_id if succefull. Otherwise, return 0
    public int loginPeer(String username, String password, Socket socket) {
        // Make sure the peer is registered
        boolean found = false;
        SavedPeer user = null;
        for (SavedPeer peer : savedPeers) {
            if (peer.getUsername().equals(username)) {
                found = true;
                user = peer;
                break;
            }
        }
        
        if (!found) return 0;

        // Create LoggedInPeer instance
        int token = getNewTokenId();
        tokens.add(token);
        loggedInPeers.add(new LoggedInPeer(token, 
                    socket.getInetAddress().getHostAddress(), 
                    socket.getPort(), 
                    user));
        return token;
    }

    public void addPeerFiles(String username, List<String> files) {
        peerFiles.put(username, files);
    }

    public void logoutPeer(int token) {
        tokens.remove(Integer.valueOf(token));
        for (LoggedInPeer peer : loggedInPeers) {
            if (peer.getTokenId() == (token)) {
                loggedInPeers.remove(peer);
                break;
            }
        }
    }

    private int getNewTokenId() {
        int token = 0;
        Random rand = new Random();
        boolean unique = false;
        while (!unique) {
            token = rand.nextInt();
            if (token == 0)
                token++; // token_id can't be zero

            // Find if there is a similar token id
            unique = true;
            for (int i = 0; i < tokens.size(); i++) {
                if (token == tokens.get(i))
                    unique = false;
            }
        }
        return token;
    }

    // Getters and setters
    public List<SavedPeer> getSavedPeers() {
        return savedPeers;
    }

    public List<Integer> getTokens() {
        return tokens;
    }

    public List<LoggedInPeer> getLoggedInPeers() {
        return loggedInPeers;
    }

    public static void main(String[] args) {
        new Tracker();
    }
}