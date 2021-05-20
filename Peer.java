import java.net.Socket;
import java.net.ConnectException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.lang.Thread;
import java.lang.Math;

public class Peer {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private PeerInputHandler inputHandler;

    private final int PORT;
    private final String SHARED_DIR;

    // Peer username and password
    private ConcurrentHashMap<String, String> credentials;
    private List<SavedFile> files;

    public Peer(String username, String password, int port) {
        credentials = new ConcurrentHashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);
        PORT = port;

        // Create a shared_directory if one doesn't exist
        SHARED_DIR = "shared_directory/" + username + "/";
        new File(SHARED_DIR + "pieces/").mkdirs();
        files = new ArrayList<>();
        SavedFile file;
        for (String filename : new File(SHARED_DIR).list()) {
            if (!filename.endsWith("pieces")) {
                file = new SavedFile(filename, true);
                files.add(file);
                partition(file);
            }
        }

        // Start the input handler, in order to be able to receive checkActive
        // and download requests.
        inputHandler = new PeerInputHandler(this);
        Thread inputHandlerThread = new Thread(inputHandler);
        inputHandlerThread.start();
        
        start();
    }

    public void start() {
        // ADD THE FUNCTIONS YOU WANT THE PEER TO EXECUTE HERE!
        boolean success = register();
        if (success)
            login();
        if (credentials.get("username").equals("testPeer")) {
            System.out.println(list());
            HashMap<String, List<Object>> fileDetails = details("file2.txt");
            simpleDownload("file2.txt", fileDetails);
        }
    }

    public boolean register() {
        try {
            // Connect to the tracker
            socket = new Socket("127.0.0.1", 55217);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());

            // Send "register" message
            output.writeUTF("register");
            output.flush();

            // Wait for true
            boolean response = input.readBoolean(); 
            if (response) {
                // Send username and password
                output.writeObject(credentials);
                output.flush();

                System.out.println("Peer: Registering...");
            } else {
                System.out.println("Peer: Unable to register with username " + credentials.get("username") + ".");
                return false;
            }

            // Wait for response message
            response = input.readBoolean();

            // Close sockets and streams
            output.close();
            input.close();
            socket.close();

            if (response)
                System.out.println("Peer: Registered with username " + credentials.get("username") + ".");
            else
                System.out.println("Peer: Unable to register with username " + credentials.get("username") + ".");

            return response;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public boolean login() {
        try {
            // Connect to the tracker
            socket = new Socket("127.0.0.1", 55217);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());

            // Send "login"
            output.writeUTF("login");
            output.flush();

            // Wait for true
            boolean response = input.readBoolean();
            if (response) {
                // Send port
                output.writeInt(PORT);
                output.flush();

                // Send username and password
                output.writeObject(credentials);
                output.flush();
                
            } else {
                System.out.println("Peer: Unable to login.");
                return false;
            }

            // Wait for response
            int tokenId = input.readInt();
            if (tokenId == 0) {
                System.out.println("Peer: Unable to login.");
                return false;
            } else {
                System.out.println("Peer " + tokenId +": Logged in.");
                credentials.put("token_id", String.valueOf(tokenId));
            }

            // Send inform
            output.writeObject(files);
            output.flush();

            // Wait for true
            response = input.readBoolean();
            return response;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public boolean logout() {
        try {
            // Send "logout"
            output.writeUTF("logout");
            output.flush();

            // Wait for true
            boolean response = input.readBoolean();
            if (!response) {
                System.out.println("Peer " + credentials.get("token_id") + ": Unable to logout.");
                return false;
            }

            // Send token_id
            output.writeInt(Integer.parseInt(credentials.get("token_id")));
            output.flush();

            // Wait for response
            response = input.readBoolean();
            if (response)
                System.out.println("Peer: Logout successful.");
            else
                System.out.println("Peer: Unable to logout.");

            output.close();
            input.close();
            socket.close();
            return response;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public boolean notifyFiles() {
        try {
            // Send "notify"
            output.writeUTF("notify");
            output.flush();

            // Send token_id
            output.writeUTF(credentials.get("token_id"));

            // Send a list with all availables files
            output.writeObject(files);
            output.flush();

            // Wait for true
            boolean response = input.readBoolean();
            if (response)
                System.out.println("Peer " + credentials.get("token_id") + ": Successfully notified tracker.");
            else
                System.out.println("Peer " + credentials.get("token_id") + ": Failed to notify tracker.");

            return response;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public List<String> list() {
        try {
            // Send list
            output.writeUTF("list");
            output.flush();

            // Wait for the list containing all available files
            Object response = input.readObject();
            @SuppressWarnings("unchecked")
            List<String> allFiles = (List<String>) response;
            System.out.println("Peer " + credentials.get("token_id") + ": Received file list from tracker.");
            return allFiles;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public HashMap<String, List<Object>> details(String filename) {
        try {
            // Send "details" and the filename
            output.writeUTF("details");
            output.writeUTF(filename);
            output.flush();

            // Response about the existance of the requested file
            boolean exists = input.readBoolean();
            if (!exists) {
                System.out.println("Peer " + credentials.get("token_id") + ": File " + filename + " doesn't exist.");
                return null;
            }

            // Wait for the list with the requested file's details
            Object response = input.readObject();
            @SuppressWarnings("unchecked")
            HashMap<String, List<Object>> fileDetails = (HashMap<String, List<Object>>) response;
            System.out.println("Peer " + credentials.get("token_id") + ": Received details for file " + filename + ".");
            return fileDetails;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean checkActive(String address, int port) {
        try {
            // Connect with the otherPeer and open IO streams
            Socket otherPeer = new Socket(address, port);
            ObjectOutputStream otherOutput = new ObjectOutputStream(otherPeer.getOutputStream());
            ObjectInputStream otherInput = new ObjectInputStream(otherPeer.getInputStream());
            // Send "checkActive"
            otherOutput.writeUTF("checkActive");
            otherOutput.flush();

            // Wait for response
            boolean response = otherInput.readBoolean();

            // Close the connection
            otherOutput.close();
            otherInput.close();
            otherPeer.close();

            if (response) {
                System.out.println("Peer " + credentials.get("token_id") + ": Peer at " + address + ":" + String.valueOf(port) + " is active.");
                return true;
            }
            return false;

        } catch(ConnectException e) {
            System.out.println("Peer: Peer at " + address + ":" + String.valueOf(port) + " is not active.");
            return false;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public void simpleDownload(String filename, HashMap<String, List<Object>> fileDetails) {
        // Exit if no the requested file isn't owned by any peers
        if (fileDetails == null) return;

        // Find the best peer
        List<Object> peerInfo;
        List<Double> scores = new ArrayList<>();
        double score;
        long start, end, responseTime;
        boolean isActive, found = false;
        for (String peer : fileDetails.keySet()) {
            peerInfo = fileDetails.get(peer);

            // Check peer state and calculate time
            start = System.currentTimeMillis();
            isActive = checkActive((String) peerInfo.get(0), (Integer) peerInfo.get(1));
            if (isActive) {
                end = System.currentTimeMillis();
                found = true;
                responseTime = end - start;

                // Calculate the score
                score = responseTime * Math.pow(0.9, (Integer) peerInfo.get(3)) * 
                        Math.pow(1.2, (Integer) peerInfo.get(4));
                peerInfo.add(score);
                scores.add(score);
            }
        }

        // Sort the scores in ascending order
        Collections.sort(scores);
        
        boolean exists, success = false;
        int bytesRead;
        byte[] bytes = new byte[4096];
        if (found) {
            Socket otherPeer;
            ObjectOutputStream otherOutput;
            ObjectInputStream otherInput;

            for (double s : scores) {
                // Get the rest of data using the score
                for (String peer : fileDetails.keySet()) {
                    peerInfo = fileDetails.get(peer);
                    if (peerInfo.size() == 5) continue;

                    if (s == (Double) peerInfo.get(6)) {
                        try {
                            // Connect with the other peer
                            otherPeer = new Socket((String) peerInfo.get(0), (Integer) peerInfo.get(1));
                            otherOutput = new ObjectOutputStream(otherPeer.getOutputStream());
                            otherInput = new ObjectInputStream(otherPeer.getInputStream());

                            // Send "simpleDownload"
                            otherOutput.writeUTF("simpleDownload");
                            otherOutput.flush();

                            // Send the filename
                            otherOutput.writeUTF(filename);
                            otherOutput.flush();

                            // Wait to learn whether the peer has the file
                            exists = otherInput.readBoolean();
                            if (exists) {
                                // Receive the file
                                FileOutputStream fos = new FileOutputStream("shared_directory/" + credentials.get("username") + "/" + filename);
                                BufferedOutputStream bos = new BufferedOutputStream(fos);
                                while ((bytesRead = otherInput.read(bytes, 0, bytes.length)) > 0)
                                    bos.write(bytes, 0, bytesRead);
                                
                                bos.close();
                                System.out.println("Peer " + credentials.get("token_id") +": Received file " + filename + " from peer " + peerInfo.get(2) + ".");
                            }

                            // Notify the tracker about the new file
                            notifyFiles();

                            // Notify the tracker about a successful download
                            notifyDownload(true, (String) peerInfo.get(2));

                            success = true;
                            break;
                            
                        } catch (IOException ioe) {
                            ioe.printStackTrace();

                            // Notify the tracker about a download failure
                            notifyDownload(false, (String) peerInfo.get(2));
                        }
                    }
                }
                if (success) break;
            }
        } else {
            System.out.println("Peer " + credentials.get("token_id") + ": Failed to download " + filename + ".");
        }
    }

    public void notifyDownload(boolean success, String username) {
        try {
            // Send "notifyDownload", success and the username
            output.writeUTF("notifyDownload");
            output.flush();
            output.writeBoolean(success);
            output.writeUTF(username);
            output.flush();
            System.out.println("Peer " + credentials.get("token_id") + ": Notified tracker for download status.");

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // Split a file into 1 MB pieces
    private void partition(SavedFile file) {
        try {
            // Do not partition files smaller than 1 MB
            File wholeFile = new File(SHARED_DIR + file.getFilename());
            if (wholeFile.length() <= 1048576) {
                java.nio.file.Files.copy(wholeFile.toPath(), new File(SHARED_DIR + "pieces/", file.getFilename()).toPath());
                file.addPiece(file.getFilename());
                return;
            }

            BufferedInputStream bis = new BufferedInputStream(
                new FileInputStream(wholeFile)
            );
            BufferedOutputStream bos;
            byte[] bytes = new byte[1048576];  // 1 MB
            int count = 0;
            String pieceName;
            while (bis.read(bytes, 0, bytes.length) != -1) {
                // Create a new piece
                pieceName = getPieceName(file.getFilename(), count);
                bos = new BufferedOutputStream(
                    new FileOutputStream(
                        new File(SHARED_DIR + "pieces/" + pieceName)
                    )
                );
                bos.write(bytes);
                bos.close();
                file.addPiece(pieceName);
                count++;
            }

            // Notify the user about the partition and close input stream
            System.out.println("Peer " + credentials.get("username") + ": Partitioned file " + file.getFilename() + ".");
            bis.close();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private String getPieceName(String filename, int count) {
        char[] chars = filename.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '.') {
                return filename.substring(0, i) + "_" + count;
            }
        }
        return filename + "_" + count;
    }

    // Getters
    public ConcurrentHashMap<String, String> getCredentials() {
        return new ConcurrentHashMap<>(credentials);
    }

    public int getPort() {
        return PORT;
    }

    public String getSharedDir() {
        return SHARED_DIR;
    }

    public static void main(String[] args) {
        new Peer(args[0], args[1], Integer.parseInt(args[2]));
    }
}