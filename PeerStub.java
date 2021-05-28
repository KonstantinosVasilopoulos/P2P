import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.lang.Thread;

public class PeerStub implements Runnable {
    private Peer peer;

    public PeerStub(Peer peer) {
        this.peer = peer;
    }

    public void run () {
        try {
            // Listen for new messages
            ServerSocket server = new ServerSocket(peer.getPort());
            System.out.println("Peer " + peer.getCredentials().get("username") + ": Listening at port " + peer.getPort() + ".");

            Thread handler;
            while (true) {
                // Receive a new connection and start a new handler
                Socket socket = server.accept();
                handler = new Thread(new PeerInputHandler(peer, socket));
                handler.start();
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}