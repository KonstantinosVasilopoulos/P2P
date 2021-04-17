public class LoggedInPeer {
    private int tokenId;
    private String ipAddress;
    private int port;
    private SavedPeer user;
    
    public LoggedInPeer(int tokenId, String ipAddress, int port, SavedPeer user) {
        this.tokenId = tokenId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.user = user;
    }

    public int getTokenId() {
        return tokenId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public SavedPeer getUser() {
        return user;
    }
}