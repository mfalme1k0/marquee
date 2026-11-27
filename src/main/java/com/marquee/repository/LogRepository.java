// src/main/java/com/marquee/repository/LogRepository.java
package com.marquee.repository;

import com.marquee.model.LogEntry;

public interface LogRepository {
    void append(LogEntry entry);
}