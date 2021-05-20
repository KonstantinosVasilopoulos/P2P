import java.net.ServerSocket;
import java.net.Socket;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;

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
                message = input.readUTF();
                switch (message) {
                    case "checkActive":
                        replyCheckActive();
                        break;

                    case "simpleDownload":
                        handleSimpleDownload();
                        break;

                    default:
                        System.out.println("Peer: No such function: " + message + ".");
                }

                // Close socket and IO streams
                output.close();
                input.close();
                socket.close();
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
            System.out.println("Peer " + peer.getCredentials().get("token_id") + ": Active.");
        
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void handleSimpleDownload() {
        try {
            // Wait for the filename
            String filename = input.readUTF();

            // Check if the file exists
            File requestedFile = new File(peer.getSharedDir() + "pieces/", filename);
            if (requestedFile.exists()) {
                // Send true
                output.writeBoolean(true);
                output.flush();

                // Check if the file is too large to be send
                long fileLength = requestedFile.length();
                if (fileLength > Integer.MAX_VALUE) {
                    System.out.println("Peer " + peer.getCredentials().get("token_id") + ": File is too big.");

                    // Send false
                    output.writeBoolean(false);
                    output.flush();
                    return;
                }
                // Send the file
                byte[] bytes = new byte[(int) fileLength];
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(requestedFile));
                bis.read(bytes, 0, bytes.length);
                output.write(bytes, 0, bytes.length);
                output.flush();
                bis.close();
                System.out.println("Peer " + peer.getCredentials().get("token_id") + ": Sent file " + requestedFile.getName() + ".");

            } else {
                // Send false
                output.writeBoolean(false);
                output.flush();
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}