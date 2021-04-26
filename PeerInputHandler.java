import java.net.ServerSocket;
import java.net.Socket;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

public class PeerInputHandler implements Runnable {
    private Peer peer;
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    public PeerInputHandler(Peer peer) {
        this.peer = peer;
    }

    public void run() {
        try {
            // Listen at port 55218
            ServerSocket server = new ServerSocket(peer.getPort());
            System.out.println("Peer: Listening at port " + peer.getPort() + ".");

            String message;
            while (true) {
                // Wait for connections
                socket = server.accept();

                // Open IO streams
                output = new ObjectOutputStream(socket.getOutputStream());
                input = new ObjectInputStream(socket.getInputStream());

                // Wait for messages and categorize them
                while (socket.isConnected()) {
                    if (input.available() == 0) continue;

                    message = input.readUTF();
                    switch (message) {
                        case "checkActive":
                            replyCheckActive();
                            break;

                        default:
                            System.out.println("Peer: No such function: " + message + ".");
                    }
                }
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void replyCheckActive() {
        try {
            // Send true
            output.writeBoolean(true);
            output.flush();
            System.out.println("Peer: Active.");
        
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
