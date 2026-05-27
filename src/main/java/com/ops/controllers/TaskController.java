package com.ops.controllers;

import com.ops.models.Task;
import com.ops.dto.CreateTaskRequest;
import com.ops.enums.TaskDirection;
import com.ops.services.ITaskService;
import java.util.List;
import java.util.Optional;

/**
 * REST Controller for Task operations
 * Handles HTTP requests for creating, updating, and executing tasks with direction support
 */
public class TaskController {
    private ITaskService taskService;

    public TaskController(ITaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Create a new task with inbound or outbound direction
     * 
     * @param request the create task request containing:
     *                - name: task name (required)
     *                - description: task description (optional)
     *                - sourcePath: source file path (required)
     *                - targetPath: target file path (required)
     *                - targetServer: target server address (required)
     *                - direction: INBOUND or OUTBOUND (required)
     * @return the created task
     */
    public Task createTask(CreateTaskRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Task name cannot be empty");
        }
        if (request.getDirection() == null) {
            throw new IllegalArgumentException("Task direction (INBOUND or OUTBOUND) is required");
        }
        if (request.getSourcePath() == null || request.getSourcePath().trim().isEmpty()) {
            throw new IllegalArgumentException("Source path cannot be empty");
        }
        if (request.getTargetPath() == null || request.getTargetPath().trim().isEmpty()) {
            throw new IllegalArgumentException("Target path cannot be empty");
        }
        if (request.getTargetServer() == null || request.getTargetServer().trim().isEmpty()) {
            throw new IllegalArgumentException("Target server cannot be empty");
        }

        return taskService.createTask(request);
    }

    /**
     * Get task by ID
     * @param id the task ID
     * @return the task if found
     */
    public Optional<Task> getTask(Long id) {
        return taskService.getTaskById(id);
    }

    /**
     * Get all tasks
     * @return list of all tasks
     */
    public List<Task> getAllTasks() {
        return taskService.getAllTasks();
    }

    /**
     * Get all tasks by direction
     * @param direction the direction (INBOUND or OUTBOUND)
     * @return list of tasks with specified direction
     */
    public List<Task> getTasksByDirection(String direction) {
        TaskDirection taskDirection = TaskDirection.fromString(direction);
        return taskService.getTasksByDirection(taskDirection);
    }

    /**
     * Update an existing task
     * @param id the task ID
     * @param request the update request
     * @return the updated task
     */
    public Task updateTask(Long id, CreateTaskRequest request) {
        return taskService.updateTask(id, request);
    }

    /**
     * Delete a task
     * @param id the task ID
     */
    public void deleteTask(Long id) {
        taskService.deleteTask(id);
    }

    /**
     * Execute a task (perform the file transfer)
     * The transfer direction is determined by the task's direction property:
     * - INBOUND: source pulls file from target server's destination to source path
     * - OUTBOUND: source pushes file from source path to target server's destination
     * 
     * @param id the task ID
     * @return the executed task with updated status
     */
    public Task executeTask(Long id) {
        return taskService.executeTask(id);
    }
}
