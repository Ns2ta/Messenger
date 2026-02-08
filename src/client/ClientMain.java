package client;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 5050;

        try (ClientConnection conn = new ClientConnection(host, port)) {
            MenuClientUI ui = new MenuClientUI(conn);
            ui.run();
        }
    }
}
