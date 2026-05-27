package com.ops.models;

import com.ops.enums.TaskDirection;
import java.time.LocalDateTime;

/**
 * Task model representing a file transfer task
 */
public class Task {
    private Long id;
    private String name;
    private String description;
    private String sourcePath;
    private String targetPath;
    private String targetServer;
    private TaskDirection direction;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public Task() {
    }

    public Task(String name, String description, String sourcePath, String targetPath, 
                String targetServer, TaskDirection direction) {
        this.name = name;
        this.description = description;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.targetServer = targetServer;
        this.direction = direction;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", targetServer='" + targetServer + '\'' +
                ", direction=" + direction +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
