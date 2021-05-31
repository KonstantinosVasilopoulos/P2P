import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
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

    public PeerInputHandler(Peer peer, Socket socket) {
        this.peer = peer;
        this.socket = socket;
    }

    public void run() {
        try {
            // Open IO stream
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());

            // Handle different messages
            String message;
            while (!socket.isClosed()) {
                while (input.available() == 0) continue;

                // Wait for messages and categorize them
                message = input.readUTF();
                switch (message) {
                    case "checkActive":
                        replyCheckActive();
                        break;

                    case "simpleDownload":
                        handleSimpleDownload();
                        break;

                    case "seederServe":
                        processRequest(true);
                        break;

                    case "collaborativeDownload":
                        processRequest(false);
                        break;

                    default:
                        System.out.println("Peer " + peer.getCredentials().get("username") + ": No such function: " + message + ".");
                }
            }

            // Close socket and IO streams
            output.close();
            input.close();
            socket.close();

        } catch (IOException ioe) {
            
        }
    }

    public void sendPiece(String piece) {
        try {
            // Send true
            output.writeBoolean(true);
            output.flush();

            // Send the user's name and the piece's name 
            output.writeUTF(peer.getCredentials().get("username"));
            output.writeUTF(piece);
            output.flush();

            // Send the piece
            File requestedPiece = new File(peer.getSharedDir() + "pieces/", piece);
            BufferedInputStream bis = new BufferedInputStream(
                new FileInputStream(requestedPiece)
            );
            byte[] bytes = new byte[(int) requestedPiece.length()];
            bis.read(bytes, 0, bytes.length);
            output.write(bytes, 0, bytes.length);
            output.flush();
            bis.close();
            System.out.println("Peer " + peer.getCredentials().get("username") + ": Sent piece " + piece + ".");

            // Disconnect
            output.close();
            input.close();
            socket.close();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void cancelRequest() {
        try {
            // Send false
            output.writeBoolean(false);
            output.flush();

            // Disconnect
            output.close();
            input.close();
            socket.close();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // The variable called function determines whether seeder-serve
    // or collaborative-download will be executed.
    // True translates to seeder-serve
    @SuppressWarnings("unchecked")
    private void processRequest(boolean function) {
        try {
            // Get the filename
            String filename = input.readUTF();

            // Get the list with the requested pieces
            Object response = input.readObject();
            ArrayList<String> pieces = (ArrayList<String>) response;

            // Create a new request
            Request request = new Request(this, peer.getCredentials().get("username"), filename, pieces, function);
            peer.addRequest(request);
            System.out.println("Peer " + peer.getCredentials().get("username") + ": Created new request.");

            // Start the timer
            peer.startTimer();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
        }
    }

    private void replyCheckActive() {
        try {
            // Send true
            output.writeBoolean(true);
            output.flush();
            System.out.println("Peer " + peer.getCredentials().get("username") + ": Active.");
        
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
                    System.out.println("Peer " + peer.getCredentials().get("username") + ": File is too big.");

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
                System.out.println("Peer " + peer.getCredentials().get("username") + ": Sent file " + requestedFile.getName() + ".");

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