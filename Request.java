import java.util.List;

public class Request {
    private PeerInputHandler handler;
    private List<String> pieces;

    public Request(PeerInputHandler handler, List<String> pieces) {
        this.handler = handler;
        this.pieces = pieces;
    }

    public void execute(String piece) {
        handler.sendPiece(piece);
    }

    public List<String> getPieces() {
        return pieces;
    }

    public String toString() {
        return pieces.toString();
    }
}
