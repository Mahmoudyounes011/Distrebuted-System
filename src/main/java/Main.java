// Main.java
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.service.AuthService;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        UserRepository userRepository = new UserRepository();
        AuthService authService = new AuthService(userRepository);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("Distributed System - Coordinator Login System");

        while (running) {
            System.out.println("\n1. Register User (Manager only)");
            System.out.println("2. Login with Token");
            System.out.println("3. List All Users (Manager only)");
            System.out.println("0. Exit");
            System.out.print("Choose option: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.print("Enter your token (Manager only): ");
                    String managerToken = scanner.nextLine();
                    User manager = authService.login(managerToken);
                    if (manager != null && manager.getRole().equalsIgnoreCase("Manager")) {
                        System.out.print("New username: ");
                        String username = scanner.nextLine();
                        System.out.print("Department: ");
                        String dept = scanner.nextLine();
                        System.out.print("Role (Manager/Employee): ");
                        String role = scanner.nextLine();

                        User newUser = authService.registerUser(username, dept, role);
                        System.out.println("User registered: " + newUser);
                    } else {
                        System.out.println("Invalid or unauthorized token.");
                    }
                    break;

                case "2":
                    System.out.print("Enter token: ");
                    String loginToken = scanner.nextLine();
                    User user = authService.login(loginToken);
                    if (user != null) {
                        System.out.println("Logged in as: " + user);
                    } else {
                        System.out.println("Invalid token.");
                    }
                    break;

                case "3":
                    System.out.print("Enter your token (Manager only): ");
                    String adminToken = scanner.nextLine();
                    User admin = authService.login(adminToken);
                    if (admin != null && admin.getRole().equalsIgnoreCase("Manager")) {
                        System.out.println("All registered users:");
                        for (User u : userRepository.getAllUsers().values()) {
                            System.out.println(u);
                        }
                    } else {
                        System.out.println("Unauthorized access.");
                    }
                    break;

                case "0":
                    running = false;
                    System.out.println("Exiting...");
                    break;

                default:
                    System.out.println("Invalid option.");
            }
        }

        scanner.close();
    }
}
