package org.example.rmi;

import org.example.rmi.NodeService;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.util.Arrays;

public class NodeServerRmi implements NodeService {
    private final String department;
    private final int port;
    private static final String STORAGE_DIR = "node_storage/";

    public NodeServerRmi(int port, String department) {
        this.port = port;
        this.department = department.toLowerCase();
        initializeStorage();
        startSyncScheduler();
    }

    private void initializeStorage() {
        File dir = new File(STORAGE_DIR + department);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Failed to create storage directory");
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java NodeServerRmi <port> <department>");
            return;
        }

        try {
            int port = Integer.parseInt(args[0]);
            String department = args[1];

            NodeServerRmi server = new NodeServerRmi(port, department);
            NodeService stub = (NodeService) UnicastRemoteObject.exportObject(server, 0);

            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind("NodeService", stub);

            System.out.printf("NodeServer [%s] RMI ready on port %d%n", department, port);
        } catch (Exception e) {
            System.err.println("NodeServer exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //Return list of files 
    @Override
    public String[] getSyncList() throws RemoteException {
        File folder = new File(STORAGE_DIR + department);
        String[] files = folder.list();
        return files != null ? files : new String[0];
    }

    @Override
    public String sendFile(String fileName) throws RemoteException {
        return readWithLock(STORAGE_DIR + department + "/" + fileName);
    }

    @Override
    public boolean hasFile(String fileName) throws RemoteException {
        return new File(STORAGE_DIR + department + "/" + fileName).exists();
    }

    @Override
    public String writeFile(String fileName, String content) throws RemoteException {
        return writeWithLock(STORAGE_DIR + department + "/" + fileName, content);
    }

    @Override
    public String deleteFile(String fileName) throws RemoteException {
        return deleteWithLock(STORAGE_DIR + department + "/" + fileName);
    }

    private String writeWithLock(String filePath, String content) throws RemoteException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock()) {
            raf.setLength(0);
            raf.writeBytes(content);
            return "File saved: " + filePath;
        } catch (IOException e) {
            throw new RemoteException("Write error: " + e.getMessage());
        }
    }

    private String readWithLock(String filePath) throws RemoteException {
        File file = new File(filePath);
        if (!file.exists()) return "File not found.";

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock(0L, Long.MAX_VALUE, true)) {
            byte[] bytes = new byte[(int) raf.length()];
            raf.readFully(bytes);
            return new String(bytes);
        } catch (IOException e) {
            throw new RemoteException("Read error: " + e.getMessage());
        }
    }

    private String deleteWithLock(String filePath) throws RemoteException {
        File file = new File(filePath);
        if (!file.exists()) return "File not found.";

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock()) {
            boolean deleted = file.delete();
            return deleted ? "File deleted." : "Failed to delete";
        } catch (IOException e) {
            throw new RemoteException("Delete error: " + e.getMessage());
        }
    }

    private void startSyncScheduler() {
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

    private void syncWithOtherNodes() {
        int[] allPorts = {5001, 5002, 5003};

        for (int port : allPorts) {
            if (port == this.port) continue;

            try {
                Registry registry = LocateRegistry.getRegistry("localhost", port);
                NodeService node = (NodeService) registry.lookup("NodeService");

                String[] theirFiles = node.getSyncList();
                String[] myFiles = new File(STORAGE_DIR + department).list();

                for (String fileName : theirFiles) {
                    if (!Arrays.asList(myFiles).contains(fileName)) {
                        String content = node.sendFile(fileName);
                        writeFile(fileName, content);
                        System.out.println("Synced file: " + fileName + " from node " + port);
                    }
                }
            } catch (Exception e) {
                System.out.println("Sync failed with node " + port + ": " + e.getMessage());
            }
        }
    }
}