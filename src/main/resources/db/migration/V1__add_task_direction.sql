-- Migration script to add direction column to tasks table
-- This script adds support for inbound/outbound task direction

ALTER TABLE tasks ADD COLUMN direction VARCHAR(20) NOT NULL DEFAULT 'OUTBOUND';

-- Add check constraint to ensure only valid directions
ALTER TABLE tasks ADD CONSTRAINT chk_task_direction 
  CHECK (direction IN ('INBOUND', 'OUTBOUND'));

-- Create index for faster queries by direction
CREATE INDEX idx_tasks_direction ON tasks(direction);

-- Update existing tasks to have explicit direction (default to OUTBOUND)
UPDATE tasks SET direction = 'OUTBOUND' WHERE direction IS NULL;
