package org.example.node;

import org.example.model.CommandType;
import org.example.model.FileCommand;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Arrays;

public class NodeServer {
    private static String department;
    private static int myPort;
    private static final String STORAGE_DIR = "node_storage/";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java NodeServer <port> <department>");
            return;
        }

        myPort = Integer.parseInt(args[0]);
        department = args[1].toLowerCase();

        File dir = new File(STORAGE_DIR + department);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        startSyncScheduler();

        try (ServerSocket serverSocket = new ServerSocket(myPort)) {
            System.out.println("NodeServer for [" + department + "] listening on port " + myPort);
            while (true) {
                Socket socket = serverSocket.accept();
                handleClient(socket);
            }
        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            FileCommand command = (FileCommand) in.readObject();
            Object result = executeCommand(command);
            out.writeObject(result);

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error handling client: " + e.getMessage());
        }
    }

    private static Object executeCommand(FileCommand command) {
        String filePath = STORAGE_DIR + department + "/" + command.getFileName();

        try {
            switch (command.getType()) {
                case ADD:
                case UPDATE:
                    return writeWithLock(filePath, command.getContent());
                case DELETE:
                    return deleteWithLock(filePath);
                case SYNC_LIST:
                    File folder = new File(STORAGE_DIR + department);
                    String[] files = folder.list();
                    return files != null ? files : new String[0];
                case SEND_FILE:
                    return readWithLock(filePath);
                case VIEW:
                case HAS_FILE:
                    File checkFile = new File(filePath);
                    return checkFile.exists();
                default:
                    return "Unknown command type.";
            }
        } catch (IOException e) {
            return "File operation error: " + e.getMessage();
        }
    }

    private static String writeWithLock(String filePath, String content) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock()) {
            raf.setLength(0); // clear file
            raf.writeBytes(content);
            return "File saved with lock: " + filePath;
        }
    }

    private static String readWithLock(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) return "File not found.";

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock(0L, Long.MAX_VALUE, true)) {
            byte[] bytes = new byte[(int) raf.length()];
            raf.readFully(bytes);
            return new String(bytes);
        }
    }

    private static String deleteWithLock(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) return "File not found.";

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock()) {
            boolean deleted = file.delete();
            return deleted ? "File deleted with lock." : "Failed to delete file.";
        }
    }

    private static void startSyncScheduler() {
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(60_000);
                    System.out.println("Running sync at " + LocalDateTime.now());
                    syncWithOtherNodes();
                }
            } catch (InterruptedException e) {
                System.out.println("Sync thread interrupted: " + e.getMessage());
            }
        }).start();
    }

    private static void syncWithOtherNodes() {
        int[] allPorts = {5001, 5002, 5003};

        for (int port : allPorts) {
            if (port == myPort) continue;

            try (Socket socket = new Socket("localhost", port);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                FileCommand request = new FileCommand(CommandType.SYNC_LIST, null, department, null, department);
                out.writeObject(request);

                Object response = in.readObject();

                if (response instanceof String[]) {
                    String[] theirFiles = (String[]) response;
                    File[] localFiles = new File(STORAGE_DIR + department).listFiles();
                    String[] myFiles = localFiles != null
                            ? Arrays.stream(localFiles).map(File::getName).toArray(String[]::new)
                            : new String[0];

                    for (String fileName : theirFiles) {
                        if (!Arrays.asList(myFiles).contains(fileName)) {
                            requestFileFromNode(fileName, port);
                        }
                    }

                } else if (response instanceof String) {
                    System.out.println("Node " + port + " responded with error: " + response);
                } else {
                    System.out.println("Unknown response type from node " + port);
                }

            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Sync failed with node " + port + ": " + e.getMessage());
            }
        }
    }

    private static void requestFileFromNode(String fileName, int port) {
        try (Socket socket = new Socket("localhost", port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            FileCommand request = new FileCommand(CommandType.SEND_FILE, fileName, department, null, department);
            out.writeObject(request);

            Object result = in.readObject();
            if (result instanceof String && ((String) result).startsWith("File not found")) {
                System.out.println("File not found on node " + port + ": " + fileName);
                return;
            }

            writeWithLock(STORAGE_DIR + department + "/" + fileName, (String) result);
            System.out.println("Synced file: " + fileName + " from node " + port);

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Failed to retrieve file from node " + port + ": " + e.getMessage());
        }
    }
}
