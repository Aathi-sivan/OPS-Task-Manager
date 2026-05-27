package com.opstool.model;

import java.time.LocalDateTime;

public class ScheduledTask {
    public enum TaskType    { FILE_TRANSFER, START_SERVICE, STOP_SERVICE, RESTART_SERVICE }
    public enum TaskStatus  { PENDING, RUNNING, SUCCESS, FAILED, DISABLED }
    public enum ScheduleType { RUN_NOW, ONCE, DAILY, WEEKLY, INTERVAL_MINUTES }

    private String id;
    private String name;
    private TaskType taskType;
    private TaskStatus status;

    // Credentials
    private String sourceCredentialId;   // optional – identifies source server creds
    private String targetCredentialId;   // kept for backward-compat; may be null in new flow
    private String targetUsername;       // username entered by ops; used to look up creds_<u>.xml

    // File transfer fields
    private String sourcePath;
    private String targetPath;

    // Service fields
    private String serviceName;

    // Schedule fields
    private ScheduleType scheduleType;
    private LocalDateTime scheduledAt;
    private int intervalMinutes;
    private String cronExpression;

    // Audit
    private LocalDateTime lastRunAt;
    private String lastRunResult;
    private LocalDateTime createdAt;

    public ScheduledTask() {
        this.createdAt = LocalDateTime.now();
        this.status    = TaskStatus.PENDING;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getId()                        { return id; }
    public void   setId(String id)               { this.id = id; }

    public String getName()                      { return name; }
    public void   setName(String name)           { this.name = name; }

    public TaskType getTaskType()                { return taskType; }
    public void     setTaskType(TaskType t)      { this.taskType = t; }

    public TaskStatus getStatus()                { return status; }
    public void       setStatus(TaskStatus s)    { this.status = s; }

    public String getSourceCredentialId()        { return sourceCredentialId; }
    public void   setSourceCredentialId(String s){ this.sourceCredentialId = s; }

    public String getTargetCredentialId()        { return targetCredentialId; }
    public void   setTargetCredentialId(String s){ this.targetCredentialId = s; }

    /** Username for the target server; used to load creds_<username>.xml at runtime. */
    public String getTargetUsername()            { return targetUsername; }
    public void   setTargetUsername(String u)    { this.targetUsername = u; }

    public String getSourcePath()                { return sourcePath; }
    public void   setSourcePath(String s)        { this.sourcePath = s; }

    public String getTargetPath()                { return targetPath; }
    public void   setTargetPath(String s)        { this.targetPath = s; }

    public String getServiceName()               { return serviceName; }
    public void   setServiceName(String s)       { this.serviceName = s; }

    public ScheduleType getScheduleType()        { return scheduleType; }
    public void         setScheduleType(ScheduleType s){ this.scheduleType = s; }

    public LocalDateTime getScheduledAt()        { return scheduledAt; }
    public void          setScheduledAt(LocalDateTime d){ this.scheduledAt = d; }

    public int  getIntervalMinutes()             { return intervalMinutes; }
    public void setIntervalMinutes(int m)        { this.intervalMinutes = m; }

    public String getCronExpression()            { return cronExpression; }
    public void   setCronExpression(String c)    { this.cronExpression = c; }

    public LocalDateTime getLastRunAt()          { return lastRunAt; }
    public void          setLastRunAt(LocalDateTime d){ this.lastRunAt = d; }

    public String getLastRunResult()             { return lastRunResult; }
    public void   setLastRunResult(String r)     { this.lastRunResult = r; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void          setCreatedAt(LocalDateTime d){ this.createdAt = d; }

    @Override public String toString()           { return name; }
}
