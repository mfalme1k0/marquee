package com.marquee.logging;

import java.util.Map;

// Provides a shared interface for recording structured application events.
public interface AppLogger {

    // Records an informational event, such as a film being created e.g. "film.created".
    void info(Long userId, String event, Map<String, ?> context);

    // Records a warning, such as a validation failure or recoverable issue.
    void warn(Long userId, String event, Map<String, ?> context);

    // Records detailed diagnostic information.
    void debug(Long userId, String event, Map<String, ?> context);

    // Records an error and its associated exception.
    void error(
            Long userId,
            String event,
            Map<String, ?> context,
            Throwable cause
    );

    // ---- Convenience overloads for the common case: no user context. ----
    // Marquee has no authentication today, so most call sites use these.

    default void info(String event, Map<String, ?> context) {
        info(null, event, context);
    }

    default void warn(String event, Map<String, ?> context) {
        warn(null, event, context);
    }

    default void debug(String event, Map<String, ?> context) {
        debug(null, event, context);
    }

    default void error(String event, Map<String, ?> context, Throwable cause) {
        error(null, event, context, cause);
    }
}