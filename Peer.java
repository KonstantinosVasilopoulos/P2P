import java.net.Socket;
import java.net.ConnectException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.lang.Thread;

public class Peer {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private PeerInputHandler inputHandler;

    private final int PORT;

    // Peer username and password
    private ConcurrentHashMap<String, String> credentials;

    public Peer(String username, String password, int port) {
        credentials = new ConcurrentHashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);
        PORT = port;

        // Create a shared_directory if one doesn't exist
        new File("shared_directory/" + username + "/").mkdirs();

        // Start the input handler, in order to be able to receive checkActive
        // and download requests.
        inputHandler = new PeerInputHandler(this);
        Thread inputHandlerThread = new Thread(inputHandler);
        inputHandlerThread.start();
        
        start();
    }

    // TODO: Add a String planFilename to load commands
    public void start() {
        boolean success = register();
        if (success)
            login();
            try {
                if (credentials.get("username").equals("testPeer"))
                    new File("shared_directory/" + credentials.get("username") + "/file1.txt").createNewFile();
                // new File("shared_directory/" + credentials.get("username") + "/file2.txt").createNewFile();
                // new File("shared_directory/" + credentials.get("username") + "/file3.txt").createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
            success = notifyFiles();
            List<String> allFiles = list();
            if (!credentials.get("username").equals("testPeer")) {
                HashMap<String, List<String>> fileDetails = details("file1.txt");
                System.out.println(fileDetails);
            }
            // if (success)
            //     logout();
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
            List<String> files = new ArrayList<>();
            for (File file : new File("shared_directory/" + credentials.get("username") + "/").listFiles())
                files.add(file.getName());

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

            // Send an array with availables files
            String[] files = new File("shared_directory/" + credentials.get("username") + "/").list();
            for (int i = 0; i < files.length; i++) {
                output.writeUTF(files[i]);
            }
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

    public HashMap<String, List<String>> details(String filename) {
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
            HashMap<String, List<String>> fileDetails = (HashMap<String, List<String>>) response;
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
                System.out.println("Peer: Peer at " + address + ":" + String.valueOf(port) + " is active.");
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

    public int getPort() {
        return PORT;
    }

    public static void main(String[] args) {
        new Peer(args[0], args[1], Integer.parseInt(args[2]));
    }
}