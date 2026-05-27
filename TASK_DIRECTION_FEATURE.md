# Task Direction Feature Documentation

## Overview
This feature adds support for specifying task direction when creating file transfer tasks in OPS-Task-Manager. Tasks can now be classified as either **INBOUND** or **OUTBOUND**, determining the direction of file transfer.

## Task Direction Types

### INBOUND (Pull)
- **Definition**: Source picks the file from target server's destination and places it in source path
- **Flow**: Target Server → Source Path
- **Use Case**: Retrieving files from a remote server to your local system
- **Example**: Pulling backup files from a remote server to local storage

### OUTBOUND (Push)
- **Definition**: Source places the file in target server's destination from source path
- **Flow**: Source Path → Target Server
- **Use Case**: Sending files from your local system to a remote server
- **Example**: Pushing configuration files to production servers

## Implementation Details

### New Classes & Files

1. **TaskDirection.java** (`src/main/java/com/ops/enums/TaskDirection.java`)
   - Enum defining INBOUND and OUTBOUND direction types
   - Includes helper method `fromString()` for parsing direction values

2. **Task.java** (`src/main/java/com/ops/models/Task.java`)
   - Enhanced model with `direction` field of type `TaskDirection`
   - Constructor updated to support direction initialization

3. **CreateTaskRequest.java** (`src/main/java/com/ops/dto/CreateTaskRequest.java`)
   - DTO for creating tasks
   - Includes `direction` field for specifying task direction

4. **ITaskService.java** (`src/main/java/com/ops/services/ITaskService.java`)
   - Service interface with new method `getTasksByDirection()`
   - Enhanced `createTask()` to handle direction

5. **TaskServiceImpl.java** (`src/main/java/com/ops/services/impl/TaskServiceImpl.java`)
   - Service implementation with complete direction support
   - `executeTask()` method now executes different logic based on direction
   - New methods: `executeInboundTransfer()` and `executeOutboundTransfer()`

6. **TaskController.java** (`src/main/java/com/ops/controllers/TaskController.java`)
   - REST controller updated with validation for required `direction` field
   - New endpoint support: `GET /tasks/direction/{direction}`
   - Enhanced `executeTask()` documentation with direction-based behavior

7. **V1__add_task_direction.sql** (`src/main/resources/db/migration/V1__add_task_direction.sql`)
   - Database migration script to add `direction` column to tasks table
   - Includes constraint validation and index for performance

## API Usage Examples

### Create an INBOUND Task (Pull)
```json
POST /api/tasks
{
  "name": "Pull Backup Files",
  "description": "Pull backup files from production server",
  "sourcePath": "/local/backups",
  "targetPath": "/remote/backups",
  "targetServer": "prod-server.example.com",
  "direction": "INBOUND"
}
```

### Create an OUTBOUND Task (Push)
```json
POST /api/tasks
{
  "name": "Deploy Configuration",
  "description": "Deploy config files to production",
  "sourcePath": "/local/config",
  "targetPath": "/etc/config",
  "targetServer": "prod-server.example.com",
  "direction": "OUTBOUND"
}
```

### Get All Tasks by Direction
```
GET /api/tasks/direction/INBOUND
GET /api/tasks/direction/OUTBOUND
```

### Execute Task (Automatically uses direction)
```
POST /api/tasks/{id}/execute
```
The system will automatically determine whether to pull or push based on the task's direction.

## Validation

The system validates that:
- `direction` field is required (INBOUND or OUTBOUND)
- Valid values are case-insensitive
- Invalid direction values throw `IllegalArgumentException` with helpful error messages
- All other required fields (name, paths, server) must be provided

## Database Schema Changes

```sql
ALTER TABLE tasks ADD COLUMN direction VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND';
ALTER TABLE tasks ADD CONSTRAINT chk_task_direction CHECK (direction IN ('INBOUND', 'OUTBOUND'));
CREATE INDEX idx_tasks_direction ON tasks(direction);
```

## Benefits

1. **Clear Intent**: Explicitly define whether you're pulling or pushing files
2. **Automated Execution**: The system executes the correct transfer method based on direction
3. **Better Filtering**: Query tasks by direction to view all pull or push operations
4. **Audit Trail**: Track which tasks are inbound and which are outbound
5. **Error Prevention**: Direction must be specified, preventing ambiguous transfers

## Future Enhancements

- Add scheduled execution based on task direction
- Implement direction-specific retry policies
- Add direction-based rate limiting
- Create dashboard views for inbound vs outbound task statistics
