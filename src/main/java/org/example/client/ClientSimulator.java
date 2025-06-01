package org.example.client;

import org.example.model.CommandType;
import org.example.model.FileCommand;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.service.AuthService;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

public class ClientSimulator{
    private static final Scanner scanner = new Scanner(System.in);
    private static final UserRepository userRepository = new UserRepository();
    private static final AuthService authService = new AuthService(userRepository);

    public static void main(String[] args) {
        System.out.println("Distributed System Client Interface\n");

        User currentUser = null;
        while (currentUser == null) {
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.print("Choose option: ");
            String option = scanner.nextLine();

            switch (option) {
                case "1":
                    currentUser = handleRegister();
                    break;
                case "2":
                    currentUser = handleLogin();
                    break;
                default:
                    System.out.println("Invalid option. Try again.");
            }
        }

        // Main command loop
        boolean running = true;
        while (running) {
            System.out.println("\nAvailable Commands: ADD, UPDATE, DELETE, VIEW, EXIT");
            System.out.print("Enter command: ");
            String cmd = scanner.nextLine().toUpperCase();

            if (cmd.equals("EXIT")) {
                System.out.println("Exiting client simulation.");
                break;
            }

            try {
                CommandType type = CommandType.valueOf(cmd);

                System.out.print("Enter file name: ");
                String fileName = scanner.nextLine();

                String content = "";
                if (type == CommandType.ADD || type == CommandType.UPDATE) {
                    System.out.print("Enter file content: ");
                    content = scanner.nextLine();
                }

                String department = (type == CommandType.VIEW || type == CommandType.VIEW_ALL) ? "" : currentUser.getDepartment();

                FileCommand command = new FileCommand(type, fileName, department, content, currentUser.getUsername());

                try (Socket socket = new Socket("localhost", 6000);
                     ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    out.writeObject(command);
                    String result = (String) in.readObject();
                    System.out.println("\nResponse from Coordinator:\n" + result);

                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Error communicating with coordinator: " + e.getMessage());
                }

            } catch (IllegalArgumentException e) {
                System.out.println("Invalid command type.");
            }
        }
    }

    private static User handleRegister() {
        System.out.print("Enter manager token to register: ");
        String token = scanner.nextLine();
        User manager = authService.login(token);

        if (manager == null || !"Manager".equalsIgnoreCase(manager.getRole())) {
            System.out.println("Unauthorized. Only managers can register users.");
            return null;
        }

        System.out.print("New username: ");
        String username = scanner.nextLine();
        System.out.print("Department: ");
        String dept = scanner.nextLine();
        System.out.print("Role (Manager/Employee): ");
        String role = scanner.nextLine();

        User newUser = authService.registerUser(username, dept, role);
        System.out.println("User registered. Token: " + newUser.getToken());
        return newUser;
    }

    private static User handleLogin() {
        System.out.print("Enter token: ");
        String token = scanner.nextLine();
        User user = authService.login(token);
        if (user != null) {
            System.out.println("Logged in as: " + user.getUsername() + " [" + user.getRole() + "]"+  " [" + user.getDepartment() + "]");
        } else {
            System.out.println("Invalid token.");
        }
        return user;
    }
}
