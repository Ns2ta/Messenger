package client;

import net.Protocol;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MenuClientUI {
    private final ClientConnection conn;
    private final Scanner sc = new Scanner(System.in);

    private Long myUserId = null;
    private String myUsername = null;

    public MenuClientUI(ClientConnection conn) {
        this.conn = conn;
    }

    public void run() throws Exception {
        // сервер может прислать "OK Connected..."
        System.out.println(conn.takeLine());

        while (true) {
            if (myUserId == null) {
                if (!authMenu()) return;
            } else {
                if (!mainMenu()) return;
            }
        }
    }

    // ---------------- AUTH ----------------

    private boolean authMenu() throws Exception {
        System.out.println("\n=== Messenger ===");
        System.out.println("1) Войти (по id)");
        System.out.println("2) Зарегистрироваться");
        System.out.println("0) Выход");
        System.out.print(">> ");
        String choice = sc.nextLine().trim();

        switch (choice) {
            case "1" -> {
                System.out.print("Введите userId: ");
                String id = sc.nextLine().trim();
                String resp = conn.requestOneLine(Protocol.LOGIN + " " + id);
                if (resp.startsWith(Protocol.OK)) {
                    // OK LOGGED_IN userId=1 username=alice
                    myUserId = parseLongField(resp, "userId");
                    myUsername = parseStringField(resp, "username");
                    System.out.println("Вы вошли как " + myUsername + " (id=" + myUserId + ")");
                    return true;
                }
                System.out.println(resp);
                return true;
            }
            case "2" -> {
                System.out.print("Введите ник: ");
                String name = sc.nextLine().trim();
                if (name.isEmpty()) {
                    System.out.println("Ник пустой.");
                    return true;
                }
                String resp = conn.requestOneLine(Protocol.REGISTER + " " + name);
                if (resp.startsWith(Protocol.OK)) {
                    // OK REGISTERED userId=1 username=alice
                    myUserId = parseLongField(resp, "userId");
                    myUsername = parseStringField(resp, "username");
                    System.out.println("Зарегистрированы и вошли как " + myUsername + " (id=" + myUserId + ")");
                    return true;
                }
                System.out.println(resp);
                return true;
            }
            case "0" -> {
                conn.send(Protocol.EXIT);
                System.out.println("Пока.");
                return false;
            }
            default -> {
                System.out.println("Неверный выбор.");
                return true;
            }
        }
    }

    // ---------------- MAIN MENU ----------------

    private boolean mainMenu() throws Exception {
        System.out.println("\nВы: " + myUsername + " (id=" + myUserId + ")");
        System.out.println("1) Показать пользователей");
        System.out.println("2) Создать чат");
        System.out.println("3) Мои чаты");
        System.out.println("4) Открыть чат");
        System.out.println("5) Переименовать себя");
        System.out.println("9) Выйти из аккаунта");
        System.out.println("0) Закрыть приложение");
        System.out.print(">> ");
        String choice = sc.nextLine().trim();

        switch (choice) {
            case "1" -> showUsers();
            case "2" -> createChatWizard();
            case "3" -> showChats();
            case "4" -> openChatWizard();
            case "5" -> renameMe(); // (если добавишь команду на сервер — иначе можно убрать)
            case "9" -> { myUserId = null; myUsername = null; }
            case "0" -> { conn.send(Protocol.EXIT); return false; }
            default -> System.out.println("Неверный выбор.");
        }
        return true;
    }

    // ---------------- USERS ----------------

    private static class UserRow {
        long id;
        String name;
        boolean online;
    }

    private List<UserRow> fetchUsers() throws Exception {
        conn.send(Protocol.USERS);
        String first = conn.takeLine(); // OK USERS count=...
        if (!first.startsWith(Protocol.OK)) {
            System.out.println(first);
            return List.of();
        }

        int count = (int) parseLongField(first, "count");
        List<UserRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String line = conn.takeLine().trim(); // "id=..|name=..|online=.."
            UserRow r = new UserRow();
            r.id = parseLongBetween(line, "id=", "|");
            r.name = parseStringBetween(line, "name=", "|");
            r.online = Boolean.parseBoolean(parseStringAfter(line, "online="));
            rows.add(r);
        }
        return rows;
    }

    private void showUsers() throws Exception {
        List<UserRow> users = fetchUsers();
        if (users.isEmpty()) {
            System.out.println("(пользователей нет)");
            return;
        }
        System.out.println("\n--- Пользователи ---");
        int idx = 1;
        for (UserRow u : users) {
            String mark = (myUserId != null && u.id == myUserId) ? " (вы)" : "";
            System.out.printf("%d) %s%s  [%s]\n", idx++, u.name, mark, u.online ? "online" : "offline");
        }
    }

    // ---------------- CHATS ----------------

    private static class ChatRow {
        long chatId;
        String title;
        List<Long> participants;
    }

    private List<ChatRow> fetchMyChats() throws Exception {
        conn.send(Protocol.CHATS);
        String first = conn.takeLine(); // OK CHATS count=...
        if (!first.startsWith(Protocol.OK)) {
            System.out.println(first);
            return List.of();
        }

        int count = (int) parseLongField(first, "count");
        List<ChatRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String line = conn.takeLine().trim(); // chatId=..|title=..|participants=[...]
            ChatRow r = new ChatRow();
            r.chatId = parseLongBetween(line, "chatId=", "|");
            r.title = parseStringBetween(line, "title=", "|");
            r.participants = parseParticipants(parseStringAfter(line, "participants="));
            rows.add(r);
        }
        return rows;
    }

    private void showChats() throws Exception {
        List<ChatRow> chats = fetchMyChats();
        if (chats.isEmpty()) {
            System.out.println("(чатов нет)");
            return;
        }
        System.out.println("\n--- Мои чаты ---");
        int idx = 1;
        for (ChatRow c : chats) {
            System.out.printf("%d) %s  (chatId=%d, участников=%d)\n",
                    idx++, c.title, c.chatId, c.participants.size());
        }
    }

    // ---------------- WIZARDS ----------------

    private void createChatWizard() throws Exception {
        System.out.print("Название чата: ");
        String title = sc.nextLine().trim();
        if (title.isEmpty()) {
            System.out.println("Название пустое.");
            return;
        }

        List<UserRow> users = fetchUsers();
        // показываем только других пользователей
        List<UserRow> others = new ArrayList<>();
        for (UserRow u : users) {
            if (myUserId != null && u.id != myUserId) others.add(u);
        }

        if (others.isEmpty()) {
            System.out.println("Некого добавить. Чат будет только с вами.");
            String resp = conn.requestOneLine(Protocol.CREATE_CHAT + " " + title + " | ");
            System.out.println(resp);
            return;
        }

        System.out.println("Выберите участников (номера через пробел), 0 = никого:");
        for (int i = 0; i < others.size(); i++) {
            System.out.printf("%d) %s\n", i + 1, others.get(i).name);
        }
        System.out.print(">> ");
        String pick = sc.nextLine().trim();
        if (pick.equals("0") || pick.isEmpty()) {
            String resp = conn.requestOneLine(Protocol.CREATE_CHAT + " " + title + " | ");
            System.out.println(resp);
            return;
        }

        Set<Long> selectedIds = new LinkedHashSet<>();
        String[] tokens = pick.split("\\s+");
        for (String t : tokens) {
            int n;
            try { n = Integer.parseInt(t); } catch (Exception e) { continue; }
            if (n >= 1 && n <= others.size()) {
                selectedIds.add(others.get(n - 1).id);
            }
        }

        String idsCsv = selectedIds.stream().map(String::valueOf).reduce((a,b)->a+","+b).orElse("");
        String resp = conn.requestOneLine(Protocol.CREATE_CHAT + " " + title + " | " + idsCsv);
        System.out.println(resp);
        // чатId пользователь может не видеть, но мы дальше можем открыть чат через "Открыть чат"
    }

    private void openChatWizard() throws Exception {
        List<ChatRow> chats = fetchMyChats();
        if (chats.isEmpty()) {
            System.out.println("(чатов нет)");
            return;
        }

        System.out.println("Выберите чат:");
        for (int i = 0; i < chats.size(); i++) {
            System.out.printf("%d) %s\n", i + 1, chats.get(i).title);
        }
        System.out.print(">> ");
        String s = sc.nextLine().trim();
        int n;
        try { n = Integer.parseInt(s); } catch (Exception e) { System.out.println("Неверно."); return; }
        if (n < 1 || n > chats.size()) { System.out.println("Неверно."); return; }

        ChatRow chat = chats.get(n - 1);
        chatScreen(chat);
    }

    private void chatScreen(ChatRow chat) throws Exception {
        while (true) {
            System.out.println("\n=== " + chat.title + " ===");
            System.out.println("1) История");
            System.out.println("2) Текст");
            System.out.println("3) Voice (Drive ссылка)");
            System.out.println("4) Media (фото/видео ссылкой)");
            System.out.println("5) Files (файл ссылкой)");
            System.out.println("0) Назад");
            System.out.print(">> ");
            String c = sc.nextLine().trim();

            switch (c) {
                case "1" -> showHistory(chat.chatId);
                case "2" -> sendText(chat.chatId);
                case "3" -> sendVoiceLink(chat.chatId);
                case "4" -> sendMediaLink(chat.chatId);
                case "5" -> sendFileLink(chat.chatId);
                case "0" -> { return; }
                default -> System.out.println("Неверный выбор.");
            }
        }
    }

    private void showHistory(long chatId) throws Exception {
        conn.send(Protocol.HISTORY + " " + chatId);
        String first = conn.takeLine(); // OK HISTORY chatId=.. count=..
        if (!first.startsWith(Protocol.OK)) {
            System.out.println(first);
            return;
        }
        int count = (int) parseLongField(first, "count");
        if (count == 0) {
            System.out.println("(пусто)");
            return;
        }
        for (int i = 0; i < count; i++) {
            String line = conn.takeLine();
            renderHistoryLine(line);      // <-- новое
        }
    }

    private void renderHistoryLine(String line) {
        if (!line.contains("kind=")) {
            System.out.println(line);
            return;
        }

        String kind = getBetween(line, "kind=", "|");
        String ts = getBetween(line, "ts=", "|");
        String sender = getBetween(line, "sender=", "|"); // сервер сейчас шлёт sender=
        String status = getBetween(line, "status=", "|"); // может быть пустым

        switch (kind) {
            case "VOICE" -> {
                String title = getBetween(line, "title=", "|");
                String url = after(line, "url=");
                System.out.println("🎙 VOICE  [" + ts + "] from=" + sender + formatStatus(status));
                System.out.println("    Title: " + title);
                System.out.println("    Link : " + url);
            }
            case "MEDIA" -> {
                String title = getBetween(line, "title=", "|");
                String url = after(line, "url=");
                System.out.println("🎞 MEDIA  [" + ts + "] from=" + sender + formatStatus(status));
                System.out.println("    Title: " + title);
                System.out.println("    Link : " + url);
            }
            case "FILE" -> {
                String name = getBetween(line, "name=", "|");
                String url = after(line, "url=");
                System.out.println("📎 FILE   [" + ts + "] from=" + sender + formatStatus(status));
                System.out.println("    Name : " + name);
                System.out.println("    Link : " + url);
            }
            case "IMAGE" -> {
                String file = after(line, "file=");
                System.out.println("🖼 IMAGE  [" + ts + "] from=" + sender + formatStatus(status));
                System.out.println("    File : " + file);
            }
            default -> { // TEXT и всё остальное
                String text = after(line, "text=");
                System.out.println("💬 TEXT   [" + ts + "] from=" + sender + formatStatus(status));
                System.out.println("    " + highlightHttps(text));
            }
        }
    }

    private String formatStatus(String status) {
        return (status == null || status.isEmpty()) ? "" : " (" + status + ")";
    }


    private String highlightHttps(String text) {
        return text.replace("https://", "🔗 https://");
    }

    private String getBetween(String s, String start, String until) {
        int a = s.indexOf(start);
        if (a < 0) return "";
        a += start.length();
        int b = s.indexOf(until, a);
        if (b < 0) return s.substring(a);
        return s.substring(a, b);
    }
    private String after(String s, String start) {
        int a = s.indexOf(start);
        if (a < 0) return "";
        return s.substring(a + start.length());
    }

    private void sendMessage(long chatId) throws Exception {
        System.out.println("Введите сообщение (пусто = отмена):");
        System.out.print(">> ");
        String text = sc.nextLine();
        if (text == null || text.trim().isEmpty()) return;

        String resp = conn.requestOneLine(Protocol.SEND_TEXT + " " + chatId + " " + text);
        System.out.println(resp);
        // EVENT прилетит отдельной строкой автоматически (reader thread)
    }
    private void sendText(long chatId) throws Exception {
        System.out.println("Введите текст (пусто = отмена):");
        System.out.print(">> ");
        String text = sc.nextLine();
        if (text == null || text.trim().isEmpty()) return;

        // обычный текст может содержать любые https ссылки
        String resp = conn.requestOneLine("SEND_TEXT " + chatId + " " + text);
        System.out.println(resp);
    }

    private void sendVoiceLink(long chatId) throws Exception {
        System.out.print("Название голосового: ");
        String title = sc.nextLine().trim();
        if (title.isEmpty()) {
            System.out.println("Отмена.");
            return;
        }
        System.out.print("Ссылка Google Drive (https://drive.google.com/... или https://docs.google.com/...): ");
        String url = sc.nextLine().trim();
        if (url.isEmpty()) {
            System.out.println("Отмена.");
            return;
        }

        // Команда на сервер (title | url)
        String resp = conn.requestOneLine("SEND_VOICE_LINK " + chatId + " " + title + " | " + url);
        System.out.println(resp);
    }

    private void sendMediaLink(long chatId) throws Exception {
        System.out.print("Название медиа (например: \"video\", \"photo\"): ");
        String title = sc.nextLine().trim();
        if (title.isEmpty()) { System.out.println("Отмена."); return; }

        System.out.print("Ссылка https (на фото/видео): ");
        String url = sc.nextLine().trim();
        if (url.isEmpty()) { System.out.println("Отмена."); return; }

        String resp = conn.requestOneLine(Protocol.SEND_MEDIA_LINK + " " + chatId + " " + title + " | " + url);
        System.out.println(resp);
    }

    private void sendFileLink(long chatId) throws Exception {
        System.out.print("Имя файла (например: report.pdf): ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) { System.out.println("Отмена."); return; }

        System.out.print("Ссылка https (на файл): ");
        String url = sc.nextLine().trim();
        if (url.isEmpty()) { System.out.println("Отмена."); return; }

        String resp = conn.requestOneLine(Protocol.SEND_FILE_LINK + " " + chatId + " " + name + " | " + url);
        System.out.println(resp);
    }

    // ---------------- Optional: rename ----------------
    // Если у тебя нет команды RENAME_USER на сервере — убери пункт "5" из меню.
    private void renameMe() {
        System.out.println("Переименование сейчас не подключено (нужно добавить команду на сервер).");
    }

    // ---------------- Parsing helpers ----------------

    private static long parseLongField(String line, String field) {
        Pattern p = Pattern.compile(field + "=(\\d+)");
        Matcher m = p.matcher(line);
        if (!m.find()) throw new IllegalArgumentException("Bad response: " + line);
        return Long.parseLong(m.group(1));
    }

    private static String parseStringField(String line, String field) {
        Pattern p = Pattern.compile(field + "=([^\\s]+)");
        Matcher m = p.matcher(line);
        if (!m.find()) throw new IllegalArgumentException("Bad response: " + line);
        return m.group(1);
    }

    private static long parseLongBetween(String line, String start, String until) {
        int a = line.indexOf(start);
        if (a < 0) throw new IllegalArgumentException("Bad line: " + line);
        a += start.length();
        int b = line.indexOf(until, a);
        if (b < 0) b = line.length();
        return Long.parseLong(line.substring(a, b));
    }

    private static String parseStringBetween(String line, String start, String until) {
        int a = line.indexOf(start);
        if (a < 0) throw new IllegalArgumentException("Bad line: " + line);
        a += start.length();
        int b = line.indexOf(until, a);
        if (b < 0) b = line.length();
        return line.substring(a, b);
    }

    private static String parseStringAfter(String line, String start) {
        int a = line.indexOf(start);
        if (a < 0) return "";
        return line.substring(a + start.length()).trim();
    }

    private static List<Long> parseParticipants(String s) {
        // s like: [1, 2, 3]
        s = s.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) return List.of();
        s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty()) return List.of();
        String[] parts = s.split(",");
        List<Long> ids = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) ids.add(Long.parseLong(t));
        }
        return ids;
    }
}
