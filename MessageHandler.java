import java.net.Socket;
import java.net.ConnectException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
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

                    case "list":
                        replyList();
                        break;

                    case "details":
                        replyDetails();
                        break;

                    case "notifyDownload":
                        handleNotifyDownload();
                        break;

                    default:
                        System.out.println("Tracker: No such function: " + message +".");
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

            // Wait for peer's port number
            int port = input.readInt();

            // Wait for username and password
            Object response = input.readObject();
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, String> credentials = (ConcurrentHashMap<String, String>) response;
            int tokenId = tracker.loginPeer(credentials.get("username"), 
                                            credentials.get("password"), 
                                            port, socket, output, input);
            if (tokenId == 0) return;

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
                received = true;

                // filename will be "empty" if the peer has not files
                if (filename.equals("empty")) break;

                if (!peerFiles.get(username).contains(filename))
                    peerFiles.get(username).add(filename);
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

    private void replyList() {
        try {
            // Send a list containing all available files
            output.writeObject(tracker.getAllFiles());
            output.flush();

        } catch (IOException ioe) {
            ioe.printStackTrace();   
        }
    }

    private void replyDetails() {
        try {
            // Get the filename
            String filename = input.readUTF();

            // Find whether the file exists
            boolean isActive, found = false;
            HashMap<String, List<String>> files = new HashMap<>(tracker.getPeerFiles());
            HashMap<String, List<String>> requestedFileOwners = new HashMap<>();
            for (String username : files.keySet()) {
                if (files.get(username).contains(filename)) {
                    // Make sure the peer is logged in
                    isActive = false;
                    for (LoggedInPeer peer : tracker.getLoggedInPeers()) {
                        if (peer.getUser().getUsername().equals(username)) {
                            // Send checkActive to peer
                            isActive = checkActive(peer);
                            
                            // Amend the requestedFileOwners hashmap to include the active peer
                            if (isActive) {
                                found = true;
                                requestedFileOwners.putIfAbsent(username, new ArrayList<>());
                                requestedFileOwners.get(username).add(peer.getIpAddress());
                                requestedFileOwners.get(username).add(String.valueOf(peer.getPort()));
                                requestedFileOwners.get(username).add(peer.getUser().getUsername());
                                requestedFileOwners.get(username).add(String.valueOf(peer.getUser().getCountFailures()));
                                requestedFileOwners.get(username).add(String.valueOf(peer.getUser().getCountFailures()));
                            } else {
                                tracker.logoutPeer(peer.getTokenId());
                            }
                            break;
                        }
                    }
                }
            }

            // Notify the peer about the status of the requested file
            output.writeBoolean(found);
            output.flush();

            // Send the information about the requested files and it's peers
            if (found) {
                output.writeObject(requestedFileOwners);
                output.flush();
                System.out.println("Tracker: Sent details for file " + filename + ".");
            } else {
                System.out.println("Tracker: No such file: " + filename + ".");
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private boolean checkActive(LoggedInPeer otherPeer) {
        try {
            // Connect with the otherPeer
            Socket otherSocket = new Socket(otherPeer.getIpAddress(), otherPeer.getPort());
            ObjectOutputStream otherOutput = new ObjectOutputStream(otherSocket.getOutputStream());
            ObjectInputStream otherInput = new ObjectInputStream(otherSocket.getInputStream());

            // Send "checkActive"
            otherOutput.writeUTF("checkActive");
            otherOutput.flush();

            // Wait for response
            boolean response = otherInput.readBoolean();

            // Close the connection
            otherOutput.close();
            otherInput.close();
            otherSocket.close();

            if (response) {
                System.out.println("Tracker: Peer at " + otherPeer.getIpAddress() + ":" + String.valueOf(otherPeer.getPort()) + " is active.");
                return true;
            }
            return false;

        } catch (ConnectException e) {
            System.out.println("Tracker: Peer at " + otherPeer.getIpAddress() + ":" + String.valueOf(otherPeer.getPort()) + " is not active.");
            return false;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    private void handleNotifyDownload() {
        try {
            // Get the success and the username
            boolean success = input.readBoolean();
            String username = input.readUTF();

            // Update the saved peer
            for (SavedPeer peer : tracker.getSavedPeers()) {
                if (peer.getUsername().equals(username)) {
                    if (success) {
                        peer.incrementCountDownloads();
                        System.out.println("Tracker: Got notified for a download success for peer " + username + ".");
                    } else {
                        peer.incrementCountFailures();
                        System.out.println("Tracker: Got notified for a download failure for peer " + username + ".");
                    }
                    
                    break;
                }
            }
            
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}