import java.net.Socket;
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
        try {
            // Wait for message
            String message = input.readUTF();
            if (message.equals("register")) {
                handleRegister();
            }

            // Close socket and object streams
            output.close();
            input.close();
            socket.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void handleRegister() {
        try {
            // Send true
            output.writeBoolean(true);
            output.flush();

            // Wait for credentials
            ConcurrentHashMap<String, String> credentials = (ConcurrentHashMap<String, String>) input.readObject();

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
}