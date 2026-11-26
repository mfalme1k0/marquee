// src/main/java/com/marquee/domain/LogEntry.java
package com.marquee.model;

import java.time.LocalDateTime;

public record LogEntry(
        Long id,
        LocalDateTime occurredAt,
        String level,
        String event,
        String contextJson,
        Long userId) {

    public static LogEntry newEvent(
            LocalDateTime occurredAt,
            String level,
            String event,
            String contextJson,
            Long userId) {
        return new LogEntry(null, occurredAt, level, event, contextJson, userId);
    }
}