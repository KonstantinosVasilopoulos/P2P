import java.util.List;
import java.util.ArrayList;

public class SavedPeer {
    private String username;
    private String password;
    private int countDownloads;
    private int countFailures;
    private List<SavedFile> files;

    public SavedPeer(String username, String password) {
        this.username = username;
        this.password = password;
        this.countDownloads = 0;
        this.countFailures = 0;
        this.files = new ArrayList<>();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getCountDownloads() {
        return countDownloads;
    }

    public int getCountFailures() {
        return countFailures;
    }

    public void incrementCountDownloads() {
        countDownloads++;
    }

    public void incrementCountFailures() {
        countFailures++;
    }

    public List<SavedFile> getFiles() {
        return files;
    }

    public void setFiles(List<SavedFile> files) {
        this.files = files;
    }

    public void addFile(SavedFile file) {
        if (!files.contains(file))
            files.add(file);
    }

    public void removeFile(SavedFile file) {
        files.remove(file);
    }
}