package com.opstool.service;

import com.opstool.model.ScheduledTask;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Background scheduler that polls tasks every minute and fires them when due.
 * Supports: RUN_NOW, ONCE, DAILY, WEEKLY, INTERVAL_MINUTES.
 */
public class TaskSchedulerService {

    private static final Logger log = Logger.getLogger(TaskSchedulerService.class.getName());

    private final XmlStorageService storage;
    private final TransferService transferService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** Callback: (taskId, logLine) -> void — for UI log panel updates */
    private BiConsumer<String, String> logCallback;

    public TaskSchedulerService(XmlStorageService storage, TransferService transferService) {
        this.storage = storage;
        this.transferService = transferService;
    }

    public void setLogCallback(BiConsumer<String, String> cb) {
        this.logCallback = cb;
    }

    /** Start the background poll loop (every 60 seconds). */
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAndRunDueTasks, 5, 60, TimeUnit.SECONDS);
        log.info("Task scheduler started.");
    }

    public void stop() {
        scheduler.shutdown();
        executor.shutdown();
    }

    /** Immediately execute a task regardless of schedule. */
    public void runNow(String taskId) {
        List<ScheduledTask> tasks = storage.loadTasks();
        tasks.stream().filter(t -> t.getId().equals(taskId)).findFirst()
            .ifPresent(t -> executor.submit(() -> executeTask(t)));
    }

    private void checkAndRunDueTasks() {
        List<ScheduledTask> tasks = storage.loadTasks();
        LocalDateTime now = LocalDateTime.now();

        for (ScheduledTask task : tasks) {
            if (task.getStatus() == ScheduledTask.TaskStatus.DISABLED) continue;
            if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING) continue;

            if (isDue(task, now)) {
                // Mark running immediately before async execution to prevent double-fire
                task.setStatus(ScheduledTask.TaskStatus.RUNNING);
                storage.saveTask(task);
                final ScheduledTask t = task;
                executor.submit(() -> executeTask(t));
            }
        }
    }

    private boolean isDue(ScheduledTask task, LocalDateTime now) {
        switch (task.getScheduleType()) {
            case RUN_NOW:
                // Only runs once on next poll after being saved
                return task.getLastRunAt() == null;

            case ONCE:
                return task.getScheduledAt() != null
                    && !now.isBefore(task.getScheduledAt())
                    && task.getLastRunAt() == null;

            case DAILY: {
                // cronExpression stores "HH:mm"
                if (task.getCronExpression() == null) return false;
                LocalTime target = LocalTime.parse(task.getCronExpression(),
                    DateTimeFormatter.ofPattern("HH:mm"));
                LocalTime nowTime = now.toLocalTime();
                // Fire within the current minute window
                boolean timeMatch = nowTime.getHour() == target.getHour()
                    && nowTime.getMinute() == target.getMinute();
                if (!timeMatch) return false;
                if (task.getLastRunAt() == null) return true;
                // Don't re-run same minute
                return task.getLastRunAt().toLocalDate().isBefore(now.toLocalDate());
            }

            case WEEKLY: {
                // cronExpression stores "MON 09:00" or "TUESDAY 14:30"
                if (task.getCronExpression() == null) return false;
                String[] parts = task.getCronExpression().split(" ");
                if (parts.length < 2) return false;
                String dayName = parts[0].toUpperCase();
                LocalTime target = LocalTime.parse(parts[1], DateTimeFormatter.ofPattern("HH:mm"));
                String todayName = now.getDayOfWeek().name().substring(0, 3); // MON, TUE...
                boolean dayMatch = dayName.startsWith(todayName) || todayName.startsWith(dayName.substring(0, 3));
                boolean timeMatch = now.toLocalTime().getHour() == target.getHour()
                    && now.toLocalTime().getMinute() == target.getMinute();
                if (!dayMatch || !timeMatch) return false;
                if (task.getLastRunAt() == null) return true;
                return task.getLastRunAt().toLocalDate().isBefore(now.toLocalDate());
            }

            case INTERVAL_MINUTES: {
                if (task.getIntervalMinutes() <= 0) return false;
                if (task.getLastRunAt() == null) return true;
                return task.getLastRunAt().plusMinutes(task.getIntervalMinutes()).isBefore(now);
            }

            default:
                return false;
        }
    }

    private void executeTask(ScheduledTask task) {
        emit(task.getId(), "=== Starting task: " + task.getName() + " ===");
        boolean success;
        try {
            switch (task.getTaskType()) {
                case FILE_TRANSFER:
                    success = transferService.executeTransfer(task, line -> emit(task.getId(), line));
                    break;
                case START_SERVICE:
                case STOP_SERVICE:
                case RESTART_SERVICE:
                    success = transferService.executeServiceAction(task, line -> emit(task.getId(), line));
                    break;
                default:
                    emit(task.getId(), "[ERROR] Unknown task type: " + task.getTaskType());
                    success = false;
            }
        } catch (Exception e) {
            emit(task.getId(), "[ERROR] Unexpected error: " + e.getMessage());
            success = false;
        }

        // Persist result
        final boolean finalSuccess = success;
        List<ScheduledTask> tasks = storage.loadTasks();
        tasks.stream().filter(t -> t.getId().equals(task.getId())).findFirst().ifPresent(t -> {
            t.setLastRunAt(LocalDateTime.now());
            t.setLastRunResult(finalSuccess ? "SUCCESS" : "FAILED");
            t.setStatus(finalSuccess ? ScheduledTask.TaskStatus.SUCCESS : ScheduledTask.TaskStatus.FAILED);
            storage.saveTask(t);
        });

        emit(task.getId(), "=== Task " + task.getName() + " finished: " + (success ? "SUCCESS" : "FAILED") + " ===");
    }

    private void emit(String taskId, String line) {
        log.info("[" + taskId + "] " + line);
        if (logCallback != null) {
            logCallback.accept(taskId, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + "  " + line);
        }
    }
}
