import java.net.Socket;
import java.net.ConnectException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Random;
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

public class Peer implements Runnable {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private PeerStub stub;
    private PeerTimer timer;

    private List<Request> requests;
    private volatile boolean executingRequest;

    private final int PORT;
    private final String SHARED_DIR;

    // Peer username and password
    private ConcurrentHashMap<String, String> credentials;
    private List<SavedFile> files;

    public Peer(String username, String password, int port) {
        // Initialize variables
        credentials = new ConcurrentHashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);
        timer = new PeerTimer(this);
        requests = new ArrayList<>();
        executingRequest = false;
        PORT = port;

        // Create a shared_directory if one doesn't exist
        SHARED_DIR = "shared_directory/" + username + "/";
        new File(SHARED_DIR + "pieces/").mkdirs();
        files = new ArrayList<>();
        SavedFile file;
        ArrayList<String> pieces;
        for (String filename : new File(SHARED_DIR).list()) {
            if (!filename.endsWith("pieces")) {
                // Partition the file and store it's pieces
                file = new SavedFile(filename, true);
                pieces = partition(file);
                file.setPieces(pieces);
                files.add(file);
            }
        }

        // Start the input handler, in order to be able to receive checkActive
        // and download requests.
        stub = new PeerStub(this);
        Thread stubThread = new Thread(stub);
        stubThread.start();
    }

    @Override
    public void run() {
        // ADD THE FUNCTIONS YOU WANT THE PEER TO EXECUTE HERE!
        boolean success = register();
        if (success)
            login();

        // Sleep for 300 ms
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Download file1.txt if named peer4
        // if (credentials.get("username").equals("peer4")) {
        //     for (int i = 0; i < 5; i++)
        //         requestPiece(details("file1.txt").get("peer1"));
        //     assemble("file1.txt");

        //     // Request a file that doesn't exist
        //     HashMap<String, ArrayList<Object>> fileDetails = details("fff.txt");
        //     if (fileDetails != null)
        //         requestPiece(details("fff.txt").get("peer1"));
        // }
        
        // Get all files that reside in the network
        HashMap<String, ArrayList<Object>> fileDetails;
        List<String> missingFiles = getMissingFiles();
        SavedFile file;
        while (!missingFiles.isEmpty()) {
            fileDetails = details(select(missingFiles));

            for (String username : fileDetails.keySet()) {
                file = (SavedFile) fileDetails.get(username).get(5);
                if (file.getSeeder()) {
                    requestPiece(fileDetails.get(username));
                    assemble(file.getFilename());
                    break;
                }
            }

            missingFiles = getMissingFiles();
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
            } else {
                System.out.println("Peer " + credentials.get("username") + ": Unable to register with username " + credentials.get("username") + ".");
                return false;
            }

            // Wait for response message
            response = input.readBoolean();

            // Close sockets and streams
            output.close();
            input.close();
            socket.close();

            if (response)
                System.out.println("Peer " + credentials.get("username") + ": Registered with username " + credentials.get("username") + ".");
            else
                System.out.println("Peer " + credentials.get("username") + ": Unable to register with username " + credentials.get("username") + ".");

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
                System.out.println("Peer " + credentials.get("username") + ": Unable to login.");
                return false;
            }

            // Wait for response
            int tokenId = input.readInt();
            if (tokenId == 0) {
                System.out.println("Peer " + credentials.get("username") + ": Unable to login.");
                return false;
            } else {
                System.out.println("Peer " + credentials.get("username") +": Logged in.");
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
                System.out.println("Peer " + credentials.get("username") + ": Unable to logout.");
                return false;
            }

            // Send token_id
            output.writeInt(Integer.parseInt(credentials.get("token_id")));
            output.flush();

            // Wait for response
            response = input.readBoolean();
            if (response)
                System.out.println("Peer " + credentials.get("username") + ": Logout successful.");
            else
                System.out.println("Peer " + credentials.get("username") + ": Unable to logout.");

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

            // Send all availables files
            output.writeInt(files.size());
            output.flush();
            
            for (SavedFile file : files) {
                output.writeObject(file);
                output.flush();
            }

            // Wait for true
            boolean response = input.readBoolean();
            if (response)
                System.out.println("Peer " + credentials.get("username") + ": Successfully notified tracker.");
            else
                System.out.println("Peer " + credentials.get("username") + ": Failed to notify tracker.");

            return response;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public ArrayList<String> list() {
        try {
            // Send list
            output.writeUTF("list");
            output.flush();

            // Wait for the list containing all available files
            Object response = input.readObject();
            @SuppressWarnings("unchecked")
            ArrayList<String> allFiles = (ArrayList<String>) response;
            System.out.println("Peer " + credentials.get("username") + ": Received file list from tracker.");
            return allFiles;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Lists' structure => {ip_address: String, port: Integer, user_name: String,
    // count_downloads: Integer, count_failures: Integer, saved_file: SavedFile}
    public HashMap<String, ArrayList<Object>> details(String filename) {
        if (filename == null) return null;

        try {
            // Send "details" and the filename
            output.writeUTF("details");
            output.writeUTF(filename);
            output.flush();

            // Response about the existance of the requested file
            boolean exists = input.readBoolean();
            if (!exists) {
                System.out.println("Peer " + credentials.get("username") + ": File " + filename + " doesn't exist.");
                return null;
            }

            // Wait for the list with the requested file's details
            Object response = input.readObject();
            @SuppressWarnings("unchecked")
            HashMap<String, ArrayList<Object>> fileDetails = (HashMap<String, ArrayList<Object>>) response;
            System.out.println("Peer " + credentials.get("username") + ": Received details for file " + filename + ".");
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
                System.out.println("Peer " + credentials.get("username") + ": Peer at " + address + ":" + String.valueOf(port) + " is active.");
                return true;
            }
            return false;

        } catch(ConnectException e) {
            System.out.println("Peer " + credentials.get("username") + ": Peer at " + address + ":" + String.valueOf(port) + " is not active.");
            return false;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public void simpleDownload(String filename, HashMap<String, ArrayList<Object>> fileDetails) {
        // Exit if no the requested file isn't owned by any peers
        if (fileDetails == null) return;

        // Find the best peer
        ArrayList<Object> peerInfo;
        ArrayList<Double> scores = new ArrayList<>();
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
                                System.out.println("Peer " + credentials.get("username") +": Received file " + filename + " from peer " + peerInfo.get(2) + ".");
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
            System.out.println("Peer " + credentials.get("username") + ": Failed to download " + filename + ".");
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
            System.out.println("Peer " + credentials.get("username") + ": Notified tracker for download status.");

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public String select(List<String> missingFiles) {
        if (missingFiles.isEmpty() || missingFiles == null) return null;

        // Choose one file at random
        Random random = new Random(System.currentTimeMillis());
        return missingFiles.get(random.nextInt(missingFiles.size()));
    }

    public void requestPiece(ArrayList<Object> details) {
        if (details == null) return;

        // Find the pieces to be requested
        SavedFile file = (SavedFile) details.get(5);
        ArrayList<String> pieces = file.getPieces();
        for (SavedFile f : files) {
            if (f.getFilename().equals(file.getFilename())) {
                pieces.removeAll(f.getPieces());
                break;
            }
        }

        if (pieces.isEmpty()) return;

        String username = null;
        try {
            // Connect to the other peer and open IO streams
            Socket otherPeer = new Socket((String) details.get(0), (int) details.get(1));
            ObjectOutputStream otherOutput = new ObjectOutputStream(otherPeer.getOutputStream());
            ObjectInputStream otherInput = new ObjectInputStream(otherPeer.getInputStream());

            // Send "seederServe" or "collaborativeDownload"
            String function = file.getSeeder() ? "seederServe" : "collaborativeDownload";
            otherOutput.writeUTF(function);
            otherOutput.flush();

            // Send the filename
            otherOutput.writeUTF(file.getFilename());
            otherOutput.flush();

            // Send the list containing the pieces this peer wants
            otherOutput.writeObject(pieces);
            otherOutput.flush();

            // Wait for an answer
            while (otherInput.available() == 0) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // The answer indicates whether this peer was chosen
            // and will receive a piece
            boolean answer = otherInput.readBoolean();
            if (!answer) {
                otherPeer.close();
                return;
            }

            // Wait for the username and the piece's name
            username = otherInput.readUTF();
            String piece = otherInput.readUTF();

            // Receive the piece
            BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(new File(SHARED_DIR + "pieces/", piece))
            );
            int bytesRead;
            byte[] bytes = new byte[1024];
            while ((bytesRead = otherInput.read(bytes, 0, bytes.length)) != -1)
                bos.write(bytes, 0, bytesRead);

            // Disconnect
            otherOutput.close();
            otherInput.close();
            otherPeer.close();

            // Create a new SavedFile instance if needed
            synchronized (this) {
                boolean found = false;
                for (SavedFile f : files) {
                    if (f.getFilename().equals(file.getFilename())) {
                        f.addPiece(piece);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    SavedFile newFile = new SavedFile(file.getFilename(), false);
                    newFile.addPiece(piece);
                    files.add(newFile);
                }
            }
            
            // Notify the user
            System.out.println("Peer " + credentials.get("username") + ": Received piece " + piece + ".");
            bos.close();

            // Notify the tracker about the new piece and the success
            notifyFiles();
            notifyDownload(true, username);
            
        } catch (IOException ioe) {
            ioe.printStackTrace();

            // Notify the tracker about the failure
            if (username != null)
                notifyDownload(false, username);
        }
    }

    public void startTimer() {
        timer.start();
    }

    // Split a file into 1 MB pieces
    private ArrayList<String> partition(SavedFile file) {
        ArrayList<String> pieceNames = new ArrayList<>();
        try {
            // Do not partition files smaller than 1 MB
            File wholeFile = new File(SHARED_DIR + file.getFilename());
            if (wholeFile.length() <= 1048576) {
                java.nio.file.Files.copy(wholeFile.toPath(), new File(SHARED_DIR + "pieces/", file.getFilename()).toPath());
                file.addPiece(file.getFilename());
                pieceNames.add(file.getFilename());
                return pieceNames;
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
                pieceNames.add(pieceName);
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
            return pieceNames;

        } catch (IOException ioe) {
            ioe.printStackTrace();
            return null;
        }
    }

    private String getPieceName(String filename, int count) {
        char[] chars = filename.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '.') {
                return filename.substring(0, i) + "_" + count + filename.substring(i, chars.length);
            }
        }
        return filename + "_" + count;
    }

    private void clearRequests() {
        for (Request request : requests)
            request.cancel();

        requests.clear();
    }

    @SuppressWarnings("unchecked")
    private void assemble(String filename) {
        try {
            // Send "assemble"
            output.writeUTF("assemble");
            output.flush();

            // Send the filename
            output.writeUTF(filename);
            output.flush();

            // Wait for the pieces
            Object response = input.readObject();
            ArrayList<String> pieces = (ArrayList<String>) response;

            // Find if this peer has all the pieces
            for (SavedFile file : files) {
                if (listContainSameItems(pieces, file.getPieces())) {
                    // Assemble the file
                    BufferedOutputStream bos = new BufferedOutputStream(
                        new FileOutputStream(new File(SHARED_DIR, filename))
                    );
                    BufferedInputStream bis;
                    byte[] bytes = new byte[1048576];  // 1 MB
                    File piece;
                    for (int i = 0; i < pieces.size(); i++) {
                        // Read the piece's bytes
                        piece = new File(SHARED_DIR + "pieces/", getPieceName(filename, i));
                        if (piece.exists()) {
                            bis = new BufferedInputStream(new FileInputStream(piece));
                            bis.read(bytes, 0, bytes.length);
                            
                            // Write the bytes
                            bos.write(bytes, 0, bytes.length);
                            bis.close();
                        }
                    }

                    // This peer is now a seeder for this file
                    file.setSeeder(true);
                    notifyFiles();

                    // Notify the user about the assembly
                    System.out.println("Peer " + credentials.get("username") + ": Assembled file " + filename + ".");
                    bos.close();
                    break;
                }
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
        }
    }

    public synchronized void addRequest(Request request) {
        if (!requests.contains(request))
            requests.add(request);
    }

    // Choose and execute one request
    public synchronized void chooseRequest() {
        if (requests.isEmpty()) return;

        // Make sure a request isn't being executed right now
        while (executingRequest) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        executingRequest = true;

        // Prioritze seeder requests
        boolean seed = false;
        for (Request r : requests) {
            if (r.isSeeder()) {
                removeNonSeederRequests();
                seed = true;
                break;
            }
        }

        Random random = new Random(System.currentTimeMillis());
        Request request;
        List<String> pieces;
        String piece;
        if (seed) {  // Seeder serve
            // Choose one request at random
            request = requests.get(random.nextInt(requests.size()));

            // Choose one piece at random
            pieces = request.getPieces();
            piece = pieces.get(random.nextInt(pieces.size()));

            // Execute the request
            System.out.println("Peer " + credentials.get("username") + ": Executing request for piece " + piece + ".");
            requests.remove(request);
            clearRequests();
            request.execute(piece);

        } else {  // Collaborative download
            // Case A
            if (requests.size() == 1) {
                // Get the first and only request and choose one piece at random
                request = requests.get(0);
                pieces = request.getPieces();
                piece = pieces.get(random.nextInt(pieces.size()));

                // Execute the request
                System.out.println("Peer " + credentials.get("username") + ": Executing request for piece " + piece + ".");
                requests.remove(request);
                clearRequests();
                request.execute(piece);
            }
        }

        executingRequest = false;
    }

    // Remove all non-seeder requests from the list containing all requests
    private void removeNonSeederRequests() {
        List<Request> toRemove = new ArrayList<>();
        for (Request request: requests) {
            if (!request.isSeeder())
                toRemove.add(request);
        }

        requests.removeAll(toRemove);
    }

    private List<String> getMissingFiles() {
        ArrayList<String> allFiles = list();

        // Find the files currently owned
        List<String> ownedFiles = new ArrayList<>();
        for (SavedFile file : files) {
            if (file.getSeeder())
                ownedFiles.add(file.getFilename());
        }

        allFiles.removeAll(ownedFiles);
        return allFiles;
    }

    private boolean listContainSameItems(List<String> firstList, List<String> secondList) {
        for (String item : firstList) {
            if (!secondList.contains(item))
                return false;
        }
        return true;
    }

    // Getters
    public ConcurrentHashMap<String, String> getCredentials() {
        return new ConcurrentHashMap<>(credentials);
    }

    public List<SavedFile> getFiles () {
        return files;
    }

    public List<Request> getRequests() {
        return new ArrayList<>(requests);
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