package org.example.model;

import java.io.Serializable;

public class FileCommand implements Serializable {
    private CommandType type;
    private String fileName;
    private String department;
    private String content;
    private String requestedBy;

    public FileCommand(CommandType type, String fileName, String department, String content, String requestedBy) {
        this.type = type;
        this.fileName = fileName;
        this.department = department;
        this.content = content;
        this.requestedBy = requestedBy;
    }

    public CommandType getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDepartment() {
        return department;
    }

    public String getContent() {
        return content;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    @Override
    public String toString() {
        return "FileCommand{" +
                "type=" + type +
                ", fileName='" + fileName + '\'' +
                ", department='" + department + '\'' +
                ", requestedBy='" + requestedBy + '\'' +
                '}';
    }
}
