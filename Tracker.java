import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;

public class Tracker {
    private ServerSocket server;

    // Data
    private ExecutorService socketPool;
    private List<SavedPeer> savedPeers;

    public Tracker() {
        socketPool = Executors.newFixedThreadPool(10);
        savedPeers = new ArrayList<>();

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

    public List<SavedPeer> getSavedPeers() {
        return savedPeers;
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

    public static void main(String[] args) {
        Tracker tracker = new Tracker();
    }
}