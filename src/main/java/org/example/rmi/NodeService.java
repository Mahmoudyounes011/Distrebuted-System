// NodeService.java
package org.example.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NodeService extends Remote {
    /* Return all nodes names inside directory,
       It uses to compare the files between nodes */
    String[] getSyncList() throws RemoteException;

    //Return file as string, Send files between nodes
    String sendFile(String fileName) throws RemoteException;

    //Verification if this node has the file or no, It uses from coordinator for a view command.
    boolean hasFile(String fileName) throws RemoteException;

    //Write the content on disk with lock, It uses for add and update command.
    String writeFile(String fileName, String content) throws RemoteException;

    //Delete this file with lock, It uses for delete command.
    String deleteFile(String fileName) throws RemoteException;
}