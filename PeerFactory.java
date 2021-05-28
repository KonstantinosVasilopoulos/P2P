import java.lang.Thread;

// A factory which makes localhost peers.
// Used for debugging.
public class PeerFactory {
    private static int count = 0;

    private static final String IP_ADDRESS = "127.0.0.1";
    private static final int INIT_PORT = 55218;

    public static void createPeer() {
        Thread peer = new Thread(new Peer("peer" + count, IP_ADDRESS, INIT_PORT + count));
        count++;
        peer.start();
    }

    public static void createManyPeers(int n) {
        for (int i = 0; i < n; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("PeerFactory: Creating peer at port " + (INIT_PORT + count) + " with name 'peer" + count + "'.");
            createPeer();
        }
    }

    public static void main(String[] args) {
        if (args.length > 0)
            PeerFactory.createManyPeers(Integer.parseInt(args[0]));
        else
            System.out.println("PeerFactory: No arguments given.");
    }
}