package org.example.rmi;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.model.CommandType;
import org.example.model.FileCommand;
import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CoordinatorRmi {
    private static final Map<String, List<Integer>> departmentNodes = new HashMap<>();
    private static final Map<Integer, Integer> nodeLoad = new HashMap<>();
    private static final Queue<FileCommand> retryQueue = new ConcurrentLinkedQueue<>();
    private static final String USERS_FILE = "src/data/users.json";

    static {
        departmentNodes.put("development", Arrays.asList(5001));
        departmentNodes.put("qa", Arrays.asList(5002));
        departmentNodes.put("design", Arrays.asList(5003));
    }

    public static void main(String[] args) {
        new Thread(CoordinatorRmi::processRetryQueue).start();

        try (ServerSocket serverSocket = new ServerSocket(6000)) {
            System.out.println("Coordinator listening on port 6000");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            System.out.println("Coordinator error: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            FileCommand command = (FileCommand) in.readObject();
            System.out.println("Received command: " + command);

            Map<String, Object> user = validateUser(command.getRequestedBy());
            if (user == null) {
                out.writeObject("User not found");
                return;
            }

            Object result = processCommand(command, user);
            out.writeObject(result);

        } catch (Exception e) {
            System.out.println("Error handling client: " + e.getMessage());
        }
    }

    private static Object processCommand(FileCommand command, Map<String, Object> user) {
        try {
            if (command.getType() == CommandType.VIEW) {
                return handleViewCommand(command);
            }

            if (!validateDepartmentAccess(command, user)) {
                return "Access denied: Invalid department permissions";
            }

            NodeService node = selectNode(command.getDepartment().toLowerCase());
            return executeNodeCommand(command, node);
        } catch (Exception e) {
            return "Error processing command: " + e.getMessage();
        }
    }

    private static Object handleViewCommand(FileCommand command) {
        for (int port : getAllNodePorts()) {
            try {
                NodeService node = getNodeService(port);
                if (node != null && node.hasFile(command.getFileName())) {
                    incrementNodeLoad(port);
                    return node.sendFile(command.getFileName());
                }
            } catch (Exception e) {
                System.out.println("Node check failed on port " + port + ": " + e.getMessage());
            }
        }
        retryQueue.add(command);
        return "VIEW request queued. File not found or all nodes unavailable.";
    }

    private static NodeService selectNode(String department) throws Exception {
        List<Integer> ports = departmentNodes.getOrDefault(department, Collections.emptyList());
        List<Integer> alivePorts = new ArrayList<>();

        for (int port : ports) {
            if (isNodeAlive(port)) alivePorts.add(port);
        }

        if (alivePorts.isEmpty()) {
            throw new Exception("All nodes for department " + department + " are down");
        }

        int selectedPort = alivePorts.stream()
                .min(Comparator.comparingInt(p -> nodeLoad.getOrDefault(p, 0)))
                .orElse(alivePorts.get(0));

        incrementNodeLoad(selectedPort);
        return getNodeService(selectedPort);
    }

    private static Object executeNodeCommand(FileCommand command, NodeService node) {
        try {
            switch (command.getType()) {
                case ADD:
                case UPDATE:
                    return node.writeFile(command.getFileName(), command.getContent());
                case DELETE:
                    return node.deleteFile(command.getFileName());
                default:
                    return "Unsupported command type";
            }
        } catch (RemoteException e) {
            return "Node operation failed: " + e.getMessage();
        }
    }

    // Helper Methods
    private static NodeService getNodeService(int port) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", port);
            return (NodeService) registry.lookup("NodeService");
        } catch (Exception e) {
            System.out.println("Failed to connect to node on port " + port + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean isNodeAlive(int port) {
        return getNodeService(port) != null;
    }

    private static Map<String, Object> validateUser(String username) {
        Map<String, Map<String, Object>> users = loadUsers();
        return users.values().stream()
                .filter(u -> u.get("username").toString().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }

    private static boolean validateDepartmentAccess(FileCommand cmd, Map<String, Object> user) {
        String userDept = user.get("department").toString().toLowerCase();
        String cmdDept = cmd.getDepartment().toLowerCase();
        return userDept.equals(cmdDept);
    }

    private static Map<String, Map<String, Object>> loadUsers() {
        try (Reader reader = new FileReader(USERS_FILE)) {
            Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
            return new Gson().fromJson(reader, type);
        } catch (IOException e) {
            System.out.println("Failed to load users: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private static List<Integer> getAllNodePorts() {
        List<Integer> ports = new ArrayList<>();
        departmentNodes.values().forEach(ports::addAll);
        return ports;
    }

    private static void incrementNodeLoad(int port) {
        nodeLoad.put(port, nodeLoad.getOrDefault(port, 0) + 1);
    }

    private static void processRetryQueue() {
        while (true) {
            try {
                Thread.sleep(30000);
                retryPendingRequests();
            } catch (InterruptedException e) {
                System.out.println("Retry thread interrupted: " + e.getMessage());
            }
        }
    }

    private static void retryPendingRequests() {
        if (retryQueue.isEmpty()) return;

        System.out.println("Retrying failed requests...");
        Iterator<FileCommand> iterator = retryQueue.iterator();

        while (iterator.hasNext()) {
            FileCommand cmd = iterator.next();
            if (processRetryCommand(cmd)) {
                iterator.remove();
            }
        }
    }

    private static boolean processRetryCommand(FileCommand cmd) {
        if (cmd.getType() == CommandType.VIEW) {
            return retryViewCommand(cmd);
        }
        return retryDepartmentCommand(cmd);
    }

    private static boolean retryViewCommand(FileCommand cmd) {
        for (int port : getAllNodePorts()) {
            try {
                NodeService node = getNodeService(port);
                if (node != null && node.hasFile(cmd.getFileName())) {
                    forwardToNode(new FileCommand(CommandType.SEND_FILE, cmd.getFileName(), "", "", cmd.getRequestedBy()), port);
                    return true;
                }
            } catch (Exception e) {
                System.out.println("Retry failed on port " + port + ": " + e.getMessage());
            }
        }
        return false;
    }

    private static boolean retryDepartmentCommand(FileCommand cmd) {
        List<Integer> candidates = departmentNodes.get(cmd.getDepartment().toLowerCase());
        if (candidates == null) return false;

        for (int port : candidates) {
            try {
                NodeService node = getNodeService(port);
                if (node != null) {
                    forwardToNode(cmd, port);
                    return true;
                }
            } catch (Exception e) {
                System.out.println("Retry failed on port " + port + ": " + e.getMessage());
            }
        }
        return false;
    }

    private static void forwardToNode(FileCommand command, int port) {
        try {
            NodeService node = getNodeService(port);
            if (node != null) {
                executeNodeCommand(command, node);
                incrementNodeLoad(port);
            }
        } catch (Exception e) {
            System.out.println("Forwarding command failed: " + e.getMessage());
        }
    }
}