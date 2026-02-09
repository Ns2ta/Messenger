package server;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int port = 5050;
        ChatServer server = new ChatServer(port);
        server.start();
    }
}
