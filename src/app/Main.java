package app;

import repository.inmemory.InMemoryChatRepository;
import repository.inmemory.InMemoryUserRepository;
import service.ChatService;
import service.UserService;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // wiring (создание зависимостей)
        UserService userService = new UserService(new InMemoryUserRepository());
        ChatService chatService = new ChatService(new InMemoryChatRepository(), userService);

        // запуск консольного приложения
        ConsoleApp app = new ConsoleApp(userService, chatService, new Scanner(System.in));
        app.run();
    }
}
