import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public class SavedFile implements Serializable {
    private String filename;
    private final boolean seeder;
    private List<String> pieces;

    public SavedFile(String filename, boolean seeder) {
        this.filename = filename;
        this.seeder = seeder;
        this.pieces = new ArrayList<>();
    }

    public String getFilename() {
        return filename;
    }

    public boolean getSeeder() {
        return seeder;
    }

    public List<String> getPieces() {
        return pieces;
    }

    public void addPiece(String piece) {
        pieces.add(piece);
    }
}