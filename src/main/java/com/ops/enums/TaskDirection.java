package com.ops.enums;

/**
 * Enum representing the direction of a task transfer.
 * 
 * INBOUND: Source picks the file from target server's destination and places it in source path
 * OUTBOUND: Source places the file in target server's destination from source path
 */
public enum TaskDirection {
    INBOUND("Inbound - Pull from target to source"),
    OUTBOUND("Outbound - Push from source to target");

    private final String description;

    TaskDirection(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse string to TaskDirection enum
     * @param value the string value to parse
     * @return TaskDirection enum value
     * @throws IllegalArgumentException if value is not valid
     */
    public static TaskDirection fromString(String value) {
        for (TaskDirection direction : TaskDirection.values()) {
            if (direction.name().equalsIgnoreCase(value)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Invalid task direction: " + value + 
            ". Valid values are: INBOUND, OUTBOUND");
    }
}
