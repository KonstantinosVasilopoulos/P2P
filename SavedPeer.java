public class SavedPeer {
    private String username;
    private String password;
    private int countDownloads;
    private int countFailures;

    public SavedPeer(String username, String password) {
        this.username = username;
        this.password = password;
        this.countDownloads = 0;
        this.countFailures = 0;
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

    public void incrementCountDownloads() {
        countDownloads++;
    }

    public void incrementCountFailures() {
        countFailures++;
    }
}