package client;

import net.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientConnection implements Closeable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    private final BlockingQueue<String> responses = new LinkedBlockingQueue<>();
    private final Thread readerThread;

    public ClientConnection(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        this.readerThread = new Thread(this::readerLoop, "server-reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    private void readerLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(Protocol.EVENT)) {
                    System.out.println();
                    renderEvent(line);
                    System.out.print(">> ");
                } else {
                    responses.offer(line);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void renderEvent(String line) {
        if (line.startsWith("EVENT NEW_TEXT")) {
            String chatId = getField(line, "chatId");
            String sender = firstNonEmpty(getField(line, "sender"), getField(line, "senderId"));
            String text = getAfter(line, "text=");
            String chatTitle = getValue(line, "chatTitle");
            String chatLabel = (chatTitle != null && !chatTitle.isBlank())
                    ? unescape(chatTitle)
                    : ("chat " + chatId);

            System.out.println("💬 NEW TEXT (" + chatLabel + ", from " + sender + ")");
            System.out.println("    " + highlightHttps(unescape(text)));
            return;
        }

        if (line.startsWith("EVENT NEW_VOICE")) {
            String chatId = getField(line, "chatId");
            String sender = firstNonEmpty(getField(line, "sender"), getField(line, "senderId"));
            String title = getAfter(line, "title=");
            String url = getAfter(line, "url=");

            int cut = title.indexOf(" url=");
            if (cut >= 0) title = title.substring(0, cut);

            String chatTitle = getValue(line, "chatTitle");
            String chatLabel = (chatTitle != null && !chatTitle.isBlank())
                    ? unescape(chatTitle)
                    : ("chat " + chatId);
            System.out.println("🎙 NEW VOICE (chat " + chatLabel + ", from " + sender + ")");
            System.out.println("    Title: " + unescape(title));
            System.out.println("    Link : " + unescape(url));
            return;
        }

        if (line.startsWith("EVENT NEW_MEDIA")) {
            String chatId = getField(line, "chatId");
            String sender = firstNonEmpty(getField(line, "sender"), getField(line, "senderId"));
            String title = getAfter(line, "title=");
            String url = getAfter(line, "url=");

            int cut = title.indexOf(" url=");
            if (cut >= 0) title = title.substring(0, cut);

            String chatTitle = getValue(line, "chatTitle");
            String chatLabel = (chatTitle != null && !chatTitle.isBlank())
                    ? unescape(chatTitle)
                    : ("chat " + chatId);
            System.out.println("🎞 NEW MEDIA (chat " + chatLabel + ", from " + sender + ")");
            System.out.println("    Title: " + unescape(title));
            System.out.println("    Link : " + unescape(url));
            return;
        }

        if (line.startsWith("EVENT NEW_FILE")) {
            String chatId = getField(line, "chatId");
            String sender = firstNonEmpty(getField(line, "sender"), getField(line, "senderId"));
            String name = getAfter(line, "name=");
            String url = getAfter(line, "url=");

            int cut = name.indexOf(" url=");
            if (cut >= 0) name = name.substring(0, cut);

            String chatTitle = getValue(line, "chatTitle");
            String chatLabel = (chatTitle != null && !chatTitle.isBlank())
                    ? unescape(chatTitle)
                    : ("chat " + chatId);
            System.out.println("📎 NEW FILE (chat " + chatLabel + ", from " + sender + ")");
            System.out.println("    Name : " + unescape(name));
            System.out.println("    Link : " + unescape(url));
            return;
        }

        if (line.startsWith("EVENT NEW_IMAGE")) {
            String chatId = getField(line, "chatId");
            String sender = firstNonEmpty(getField(line, "sender"), getField(line, "senderId"));
            String file = getAfter(line, "file=");

            String chatTitle = getValue(line, "chatTitle");
            String chatLabel = (chatTitle != null && !chatTitle.isBlank())
                    ? unescape(chatTitle)
                    : ("chat " + chatId);
            System.out.println("🖼 NEW IMAGE (chat " + chatLabel + ", from " + sender + ")");
            System.out.println("    File : " + unescape(file));
            return;
        }

        System.out.println(line);
    }

    private String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }

    private String getField(String line, String key) {
        int idx = line.indexOf(key + "=");
        if (idx < 0) return "";
        idx += (key.length() + 1);
        int end = line.indexOf(' ', idx);
        if (end < 0) end = line.length();
        return line.substring(idx, end);
    }

    private String getAfter(String line, String marker) {
        int idx = line.indexOf(marker);
        if (idx < 0) return "";
        return line.substring(idx + marker.length()).trim();
    }

    private String getValue(String line, String key) {
        int start = line.indexOf(key + "=");
        if (start < 0) return "";
        start += key.length() + 1;

        int end = line.length();
        for (int i = start; i < line.length() - 1; i++) {
            if (line.charAt(i) == ' ') {
                int eq = line.indexOf('=', i + 1);
                if (eq > 0) {
                    boolean ok = true;
                    for (int j = i + 1; j < eq; j++) {
                        if (line.charAt(j) == ' ') { ok = false; break; }
                    }
                    if (ok) { end = i; break; }
                }
            }
        }
        return line.substring(start, end).trim();
    }


    private String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\r", "\r");
    }

    private String highlightHttps(String s) {
        return s.replace("https://", "🔗 https://");
    }

    public void send(String line) {
        out.println(line);
    }

    public String takeLine() throws InterruptedException {
        return responses.take();
    }

    public String requestOneLine(String cmd) throws InterruptedException {
        send(cmd);
        return takeLine();
    }

    @Override
    public void close() throws IOException {
        try {
            out.println(Protocol.EXIT);
        } catch (Exception ignored) {}
        try { socket.close(); } catch (Exception ignored) {}
    }
}