import java.util.ArrayList;

public class Request {
    private PeerInputHandler handler;
    private String peerName;
    private String filename;
    private ArrayList<String> pieces;
    private boolean seeder;  // Indicates whether the request is for a seeder piece

    public Request(PeerInputHandler handler, String peerName, String filename,
                 ArrayList<String> pieces, boolean seeder) {
        this.handler = handler;
        this.peerName = peerName;
        this.filename = filename;
        this.pieces = pieces;
        this.seeder = seeder;
    }

    public void execute(String piece) {
        handler.sendPiece(piece);
    }

    public void cancel() {
        handler.cancelRequest();
    }

    public String getPeerName() {
        return peerName;
    }

    public String getFilename() {
        return filename;
    }

    public ArrayList<String> getPieces() {
        return pieces;
    }

    public boolean isSeeder() {
        return seeder;
    }

    public String toString() {
        return pieces.toString();
    }
}
