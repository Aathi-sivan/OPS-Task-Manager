package com.ops.services.impl;

import com.ops.models.Task;
import com.ops.dto.CreateTaskRequest;
import com.ops.enums.TaskDirection;
import com.ops.services.ITaskService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of TaskService
 * Note: This is a basic in-memory implementation. For production, use a database.
 */
public class TaskServiceImpl implements ITaskService {
    private Map<Long, Task> taskStore = new HashMap<>();
    private Long taskIdCounter = 1L;

    @Override
    public Task createTask(CreateTaskRequest request) {
        // Validate request
        if (request == null || request.getName() == null || request.getDirection() == null) {
            throw new IllegalArgumentException("Task name and direction are required");
        }

        // Create new task with direction
        Task task = new Task();
        task.setId(taskIdCounter++);
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setSourcePath(request.getSourcePath());
        task.setTargetPath(request.getTargetPath());
        task.setTargetServer(request.getTargetServer());
        task.setDirection(request.getDirection());
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        // Store task
        taskStore.put(task.getId(), task);

        System.out.println("Task created: " + task.getName() + 
            " with direction: " + task.getDirection().getDescription());

        return task;
    }

    @Override
    public Optional<Task> getTaskById(Long id) {
        return Optional.ofNullable(taskStore.get(id));
    }

    @Override
    public List<Task> getAllTasks() {
        return new ArrayList<>(taskStore.values());
    }

    @Override
    public List<Task> getTasksByDirection(TaskDirection direction) {
        return taskStore.values().stream()
            .filter(task -> task.getDirection() == direction)
            .collect(Collectors.toList());
    }

    @Override
    public Task updateTask(Long id, CreateTaskRequest request) {
        Task task = taskStore.get(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found with id: " + id);
        }

        if (request.getName() != null) {
            task.setName(request.getName());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getSourcePath() != null) {
            task.setSourcePath(request.getSourcePath());
        }
        if (request.getTargetPath() != null) {
            task.setTargetPath(request.getTargetPath());
        }
        if (request.getTargetServer() != null) {
            task.setTargetServer(request.getTargetServer());
        }
        if (request.getDirection() != null) {
            task.setDirection(request.getDirection());
        }
        task.setUpdatedAt(LocalDateTime.now());

        return task;
    }

    @Override
    public void deleteTask(Long id) {
        taskStore.remove(id);
    }

    @Override
    public Task executeTask(Long id) {
        Task task = taskStore.get(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found with id: " + id);
        }

        task.setStatus("RUNNING");

        // Execute based on direction
        if (task.getDirection() == TaskDirection.INBOUND) {
            executeInboundTransfer(task);
        } else if (task.getDirection() == TaskDirection.OUTBOUND) {
            executeOutboundTransfer(task);
        }

        task.setStatus("COMPLETED");
        task.setUpdatedAt(LocalDateTime.now());

        return task;
    }

    private void executeInboundTransfer(Task task) {
        System.out.println("Executing INBOUND transfer for task: " + task.getName());
        System.out.println("Pulling file from " + task.getTargetServer() + 
            ":" + task.getTargetPath() + " to " + task.getSourcePath());
    }

    private void executeOutboundTransfer(Task task) {
        System.out.println("Executing OUTBOUND transfer for task: " + task.getName());
        System.out.println("Pushing file from " + task.getSourcePath() + 
            " to " + task.getTargetServer() + ":" + task.getTargetPath());
    }
}
