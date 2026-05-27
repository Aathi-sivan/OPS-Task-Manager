package com.ops.dto;

import com.ops.enums.TaskDirection;

/**
 * DTO for creating a new task
 */
public class CreateTaskRequest {
    private String name;
    private String description;
    private String sourcePath;
    private String targetPath;
    private String targetServer;
    private TaskDirection direction;

    // Constructors
    public CreateTaskRequest() {
    }

    public CreateTaskRequest(String name, String description, String sourcePath, 
                            String targetPath, String targetServer, TaskDirection direction) {
        this.name = name;
        this.description = description;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.targetServer = targetServer;
        this.direction = direction;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }

    public TaskDirection getDirection() {
        return direction;
    }

    public void setDirection(TaskDirection direction) {
        this.direction = direction;
    }

    @Override
    public String toString() {
        return "CreateTaskRequest{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", targetServer='" + targetServer + '\'' +
                ", direction=" + direction +
                '}';
    }
}
