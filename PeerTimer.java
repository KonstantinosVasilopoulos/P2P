import java.lang.Thread;

public class PeerTimer {
    private Peer peer;
    private boolean operational;

    public PeerTimer(Peer peer) {
        this.peer = peer;
        this.operational = false;
    }

    public void start() {
        if (operational) return;
        operational = true;
        
        try {
            // Sleep for 200 msec
            Thread.sleep(200);

            // Choose and execute a request
            peer.chooseRequest();
            peer.clearRequests();

        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } finally {
            operational = false;
        }
    }
}