// Coordinator.java
package org.example.coordinator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.model.CommandType;
import org.example.model.FileCommand;
import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Coordinator {
    private static final Map<String, List<Integer>> departmentNodes = new HashMap<>();
    private static final Map<Integer, Integer> nodeLoad = new HashMap<>();
    private static final Queue<FileCommand> retryQueue = new ConcurrentLinkedQueue<>();

    static {
        departmentNodes.put("development", Arrays.asList(5001));
        departmentNodes.put("qa", Arrays.asList(5002));
        departmentNodes.put("design", Arrays.asList(5003));
    }

    //the main function to run the coordinator
    public static void main(String[] args) {
        int coordinatorPort = 6000;

        new Thread(Coordinator::processRetryQueue).start();

        try (ServerSocket serverSocket = new ServerSocket(coordinatorPort)) {
            System.out.println("Coordinator listening on port " + coordinatorPort);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            System.out.println("Coordinator error: " + e.getMessage());
        }
    }

    //handle request client and send to the node
    private static void handleClient(Socket clientSocket) {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            FileCommand command = (FileCommand) in.readObject();
            System.out.println("Received command: " + command);

            Map<String, Map<String, Object>> users = loadUsers();
            Map<String, Object> foundUser = null;
            for (Map<String, Object> user : users.values()) {
                if (user.get("username").toString().equalsIgnoreCase(command.getRequestedBy())) {
                    foundUser = user;
                    break;
                }
            }

            if (foundUser == null) {
                out.writeObject("User not found");
                return;
            }

            String userDept = foundUser.get("department").toString().toLowerCase();

            if (command.getType() == CommandType.VIEW) {
                boolean viewed = false;
                for (int port : getAllNodePorts()) {
                    if (!isNodeAlive(port)) continue;
                    if (nodeHasFile(command.getFileName(), port)) {
                        FileCommand readCommand = new FileCommand(
                                CommandType.SEND_FILE,
                                command.getFileName(),
                                "",
                                "",
                                command.getRequestedBy()
                        );
                        Object content = forwardToNode(readCommand, port);
                        if (content instanceof String) {
                            incrementNodeLoad(port);
                            out.writeObject(content);
                        } else {
                            out.writeObject("Unexpected response type from node.");
                        }
                        viewed = true;
                        break;
                    }
                }
                if (!viewed) {
                    retryQueue.add(command);
                    out.writeObject("VIEW request queued. File not found or all nodes unavailable.");
                }
                return;
            }

            String cmdDept = command.getDepartment().toLowerCase();
            if (!userDept.equals(cmdDept)) {
                out.writeObject("Access denied: Only VIEW is allowed for other departments.");
                return;
            }

            List<Integer> possibleNodes = departmentNodes.get(cmdDept);
            if (possibleNodes == null || possibleNodes.isEmpty()) {
                out.writeObject("Unknown department: " + cmdDept);
                return;
            }

            List<Integer> aliveNodes = new ArrayList<>();
            for (int node : possibleNodes) {
                if (isNodeAlive(node)) aliveNodes.add(node);
            }

            if (aliveNodes.isEmpty()) {
                retryQueue.add(command);
                out.writeObject("All nodes for department are down. Request added to retry queue.");
                return;
            }

            int selectedNode = selectLeastLoadedNode(aliveNodes);
            Object result = forwardToNode(command, selectedNode);
            incrementNodeLoad(selectedNode);

            if (result instanceof String) {
                out.writeObject(result);
            } else {
                out.writeObject("Unexpected response type from node.");
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error handling client: " + e.getMessage());
        }
    }

    //check the node is live or no I mean the node has socket connection By TCP
    private static boolean isNodeAlive(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    //run thread in background every 30 second to execute command for any alive node.
    private static void processRetryQueue() {
        while (true) {
            try {
                Thread.sleep(30000);
                if (!retryQueue.isEmpty()) {
                    System.out.println("Retrying failed requests...");
                    Iterator<FileCommand> iterator = retryQueue.iterator();
                    while (iterator.hasNext()) {
                        FileCommand cmd = iterator.next();
                        if (cmd.getType() == CommandType.VIEW) {
                            for (int port : getAllNodePorts()) {
                                if (!isNodeAlive(port)) continue;
                                if (nodeHasFile(cmd.getFileName(), port)) {
                                    forwardToNode(new FileCommand(CommandType.SEND_FILE, cmd.getFileName(), "", "", cmd.getRequestedBy()), port);
                                    incrementNodeLoad(port);
                                    iterator.remove();
                                    break;
                                }
                            }
                        } else {
                            List<Integer> candidates = departmentNodes.get(cmd.getDepartment().toLowerCase());
                            if (candidates != null) {
                                for (int port : candidates) {
                                    if (isNodeAlive(port)) {
                                        forwardToNode(cmd, port);
                                        incrementNodeLoad(port);
                                        iterator.remove();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("Retry thread interrupted: " + e.getMessage());
            }
        }
    }

    //get all nodes
    private static List<Integer> getAllNodePorts() {
        List<Integer> ports = new ArrayList<>();
        for (List<Integer> list : departmentNodes.values()) {
            ports.addAll(list);
        }
        return ports;
    }

    //get the least load node
    private static int selectLeastLoadedNode(List<Integer> ports) {
        return ports.stream()
                .min(Comparator.comparingInt(p -> nodeLoad.getOrDefault(p, 0)))
                .orElse(ports.get(0));
    }

    //increment node load for each node
    private static void incrementNodeLoad(int port) {
        nodeLoad.put(port, nodeLoad.getOrDefault(port, 0) + 1);
    }

    //check the node if has file
    private static boolean nodeHasFile(String fileName, int port) {
        try (Socket socket = new Socket("localhost", port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            FileCommand probe = new FileCommand(CommandType.HAS_FILE, fileName, "", "", "coordinator");
            out.writeObject(probe);
            Object response = in.readObject();
            return response instanceof Boolean && (Boolean) response;
        } catch (Exception e) {
            return false;
        }
    }

    //forward fileCommand to the specific port
    private static Object forwardToNode(FileCommand command, int port) {
        try (Socket nodeSocket = new Socket("localhost", port);
             ObjectOutputStream out = new ObjectOutputStream(nodeSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(nodeSocket.getInputStream())) {

            out.writeObject(command);
            return in.readObject();

        } catch (IOException | ClassNotFoundException e) {
            return "Failed to reach node at port " + port + ": " + e.getMessage();
        }
    }

    //To get users from file json
    private static Map<String, Map<String, Object>> loadUsers() {
        try (Reader reader = new FileReader("src/data/users.json")) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            System.out.println("Failed to load users: " + e.getMessage());
            return new HashMap<>();
        }
    }
}