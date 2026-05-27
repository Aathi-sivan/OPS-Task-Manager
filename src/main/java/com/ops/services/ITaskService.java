package com.ops.services;

import com.ops.models.Task;
import com.ops.dto.CreateTaskRequest;
import com.ops.enums.TaskDirection;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for Task operations
 */
public interface ITaskService {
    /**
     * Create a new task with specified direction
     * @param request the create task request containing task details and direction
     * @return the created task
     */
    Task createTask(CreateTaskRequest request);

    /**
     * Get task by id
     * @param id the task id
     * @return optional containing the task if found
     */
    Optional<Task> getTaskById(Long id);

    /**
     * Get all tasks
     * @return list of all tasks
     */
    List<Task> getAllTasks();

    /**
     * Get all tasks by direction
     * @param direction the task direction (INBOUND or OUTBOUND)
     * @return list of tasks with specified direction
     */
    List<Task> getTasksByDirection(TaskDirection direction);

    /**
     * Update task
     * @param id the task id
     * @param request the update request
     * @return the updated task
     */
    Task updateTask(Long id, CreateTaskRequest request);

    /**
     * Delete task
     * @param id the task id
     */
    void deleteTask(Long id);

    /**
     * Execute task
     * @param id the task id
     * @return the executed task
     */
    Task executeTask(Long id);
}
