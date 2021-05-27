import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.lang.Thread;

public class Tracker {
    private ServerSocket server;

    // Data
    private List<SavedPeer> savedPeers;
    private List<LoggedInPeer> loggedInPeers;
    private List<Integer> tokens;  // A list for holding all active token ids
    // This hashmap holds a peer's username with his files' names
    private ConcurrentHashMap<String, List<SavedFile>> peerFiles;

    public Tracker() {
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
                Thread thread = new Thread(new MessageHandler(socket, this));
                thread.start();
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(0);
        }
    }

    // Try to create a new peer. If the peer's username already exists return false.
    public synchronized boolean addSavedPeer(String username, String password) {
        for (SavedPeer peer : savedPeers) {
            if (peer.getUsername().equals(username))
                return false;
        }

        savedPeers.add(new SavedPeer(username, password));
        return true;
    }

    // Return the token_id if succefull. Otherwise, return 0
    public synchronized int loginPeer(String username, String password, int port, Socket socket,
                        ObjectOutputStream output, ObjectInputStream input) {
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
        
        if (!found || !password.equals(user.getPassword())) return 0;

        // Create LoggedInPeer instance
        int token = getNewTokenId();
        tokens.add(token);
        loggedInPeers.add(new LoggedInPeer(token, port, socket, output, input, user));
        return token;
    }

    public synchronized void addPeerFiles(String username, List<SavedFile> files) {
        peerFiles.put(username, files);

        // Find the user and add the new files to his existing ones
        for (SavedPeer user : savedPeers) {
            if (user.getUsername().equals(username)) {
                for (SavedFile file : files)
                    user.addFile(file);
                
                break;
            }
        }
    }

    public synchronized void logoutPeer(int token) {
        tokens.remove(Integer.valueOf(token));
        for (LoggedInPeer peer : loggedInPeers) {
            if (peer.getTokenId() == (token)) {
                loggedInPeers.remove(peer);
                break;
            }
        }
    }

    // Given a token_id, return the username associated with that user
    public String getUsername(String token) {
        for (LoggedInPeer user : loggedInPeers) {
            if (String.valueOf(user.getTokenId()).equals(token))
                return user.getUser().getUsername();
        }
        return null;
    }

    // Return a list containing all files available
    public List<String> getAllFiles() {
        List<String> allFiles = new ArrayList<>();
        for (String username : peerFiles.keySet()) {
            for (SavedFile file : peerFiles.get(username)) {
                if (!allFiles.contains(file.getFilename()))
                    allFiles.add(file.getFilename());
            }
        }

        return allFiles;
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

    public ConcurrentHashMap<String, List<SavedFile>> getPeerFiles() {
        return new ConcurrentHashMap<>(peerFiles);
    }

    public synchronized void setPeerFiles(ConcurrentHashMap<String, List<SavedFile>> peerFiles) {
        this.peerFiles = peerFiles;
    }

    public synchronized void addPeerFile(String token, SavedFile file) {
        peerFiles.putIfAbsent(token, new ArrayList<>());
        peerFiles.get(token).add(file);
    }

    public static void main(String[] args) {
        new Tracker();
    }
}