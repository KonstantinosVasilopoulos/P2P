import java.io.Serializable;
import java.util.ArrayList;

public class SavedFile implements Serializable {
    private String filename;
    private boolean seeder;
    private ArrayList<String> pieces;

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

    public void setSeeder(boolean seeder) {
        this.seeder = seeder;
    }

    public ArrayList<String> getPieces() {
        return new ArrayList<>(pieces);
    }

    public void setPieces(ArrayList<String> pieces) {
        this.pieces = pieces;
    }

    public void addPiece(String piece) {
        pieces.add(piece);
    }
}