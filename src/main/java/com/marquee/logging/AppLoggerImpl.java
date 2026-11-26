// src/main/java/com/marquee/logging/AppLoggerImpl.java
package com.marquee.logging;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;   // replaces JsonProcessingException
import com.marquee.model.LogEntry;
import com.marquee.repository.LogRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public final class AppLoggerImpl implements AppLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "password_hash", "passwordhash", "passwordHash",
            "current_password", "new_password");

    private final Path logFile;
    private final LogRepository logRepository;   // nullable for file-only bootstrap
    private final Gson gson;                     // <-- Gson, not ObjectMapper
    private final Clock clock;

    public AppLoggerImpl(LogRepository logRepository) {
        this(resolveLogFile(), logRepository, new Gson(), Clock.systemUTC());
    }

    public AppLoggerImpl(Path logFile, LogRepository logRepository, Gson gson, Clock clock) {
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        this.logRepository = logRepository;
        this.gson = Objects.requireNonNull(gson, "gson");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static Path resolveLogFile() {
        return resolveLogFile(System.getenv("MARQUEE_LOG_PATH"));
    }

    static Path resolveLogFile(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("marquee.log");
        }
        return Path.of(configuredPath);
    }

    @Override public void debug(Long userId, String event, Map<String, ?> context) { write(userId, "DEBUG", event, context, null); }
    @Override public void info (Long userId, String event, Map<String, ?> context) { write(userId, "INFO",  event, context, null); }
    @Override public void warn (Long userId, String event, Map<String, ?> context) { write(userId, "WARN",  event, context, null); }
    @Override public void error(Long userId, String event, Map<String, ?> context, Throwable cause) { write(userId, "ERROR", event, context, cause); }

    private void write(Long userId, String level, String event,
                       Map<String, ?> context, Throwable cause) {

        if (event == null || event.isBlank()) {
            reportLoggingFailure("Cannot write log event with a missing event name");
            return;
        }

        Map<String, Object> safeContext = new LinkedHashMap<>();
        if (context != null) safeContext.putAll(context);

        for (String field : SENSITIVE_FIELDS) {
            if (safeContext.containsKey(field)) safeContext.put(field, "[REDACTED]");
        }

        safeContext.remove("user_id");
        if (userId != null) safeContext.put("user_id", userId);
        if (cause != null) safeContext.put("error_type", cause.getClass().getSimpleName());

        LocalDateTime occurredAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String timestamp = TIMESTAMP_FORMAT.format(occurredAt.atOffset(ZoneOffset.UTC));

        String contextJson;
        try {
            contextJson = gson.toJson(safeContext);         // <-- Gson
        } catch (JsonParseException ex) {                    // <-- Gson's unchecked equivalent
            reportLoggingFailure("Could not serialize log context");
            contextJson = "{\"context_serialization_error\":true}";
        }

        String line = timestamp + " | " + String.format("%-5s", level)
                + " | " + event + " | " + formatContext(safeContext)
                + System.lineSeparator();

        writeToFile(line);
        writeToDatabase(occurredAt, level, event, contextJson, userId);
    }

    private String formatContext(Map<String, ?> context) {
        StringJoiner joiner = new StringJoiner(" ");
        for (Map.Entry<String, ?> entry : context.entrySet()) {
            String value = String.valueOf(entry.getValue())
                    .replace("\\", "\\\\").replace("\r", "\\r")
                    .replace("\n", "\\n").replace("\t", "\\t")
                    .replace("|", "\\|");
            joiner.add(entry.getKey() + "=" + value);
        }
        return joiner.toString();
    }

    private void writeToFile(String line) {
        try {
            createParentDirectories(logFile.toAbsolutePath().getParent());
            Files.writeString(logFile, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | SecurityException e) {
            reportLoggingFailure("Could not write to log file");
        }
    }

    static void createParentDirectories(Path parent) throws IOException {
        if (parent != null) Files.createDirectories(parent);
    }

    private void writeToDatabase(LocalDateTime occurredAt, String level,
                                 String event, String contextJson, Long userId) {
        if (logRepository == null) return;   // bootstrap / file-only mode
        try {
            logRepository.append(LogEntry.newEvent(occurredAt, level, event, contextJson, userId));
        } catch (RuntimeException e) {
            reportLoggingFailure("Could not persist log event: " + event
                    + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void reportLoggingFailure(String message) {
        LocalDateTime occurredAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String timestamp = TIMESTAMP_FORMAT.format(occurredAt.atOffset(ZoneOffset.UTC));

        String safeMessage = String.valueOf(message)
                .replace("\\", "\\\\").replace("\r", "\\r")
                .replace("\n", "\\n").replace("|", "\\|");

        String line = timestamp + " | ERROR | logging.failure | message="
                + safeMessage + System.lineSeparator();

        try {
            createParentDirectories(logFile.toAbsolutePath().getParent());
            Files.writeString(logFile, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | SecurityException ignored) { }

        if (logRepository == null) return;
        try {
            String contextJson = gson.toJson(Map.of("message", String.valueOf(message)));
            logRepository.append(LogEntry.newEvent(occurredAt, "ERROR",
                    "logging.failure", contextJson, null));
        } catch (Exception ignored) { }
    }
}