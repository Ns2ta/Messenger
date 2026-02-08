package client;

import net.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Console client:
 * - one instance per terminal (one user session)
 * - reader thread prints server messages (including EVENT NEW_MESSAGE)
 * - main thread reads your commands and sends to server
 */
public class ChatClient {
    private final String host;
    private final int port;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             Scanner sc = new Scanner(System.in)) {

            // reader thread: prints server lines asynchronously (push notifications)
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        // Make events visually distinct
                        if (line.startsWith(Protocol.EVENT)) {
                            System.out.println("\n" + line);
                            System.out.print("> ");
                        } else {
                            System.out.println(line);
                            System.out.print("> ");
                        }
                    }
                } catch (IOException ignored) {}
            }, "server-reader");

            reader.setDaemon(true);
            reader.start();

            // writer loop
            System.out.print("> ");
            while (true) {
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) {
                    System.out.print("> ");
                    continue;
                }

                out.println(line);

                // local exit convenience
                if (line.equalsIgnoreCase(Protocol.EXIT)) {
                    break;
                }
                System.out.print("> ");
            }
        }
    }
}
