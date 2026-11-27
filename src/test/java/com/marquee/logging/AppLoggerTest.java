package com.marquee.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Covers the no-user convenience overloads defined as default methods on {@link AppLogger}. */
class AppLoggerTest {

    private RecordingLogger logger;

    @BeforeEach
    void setUp() {
        logger = new RecordingLogger();
    }

    @Test
    void infoOverloadDelegatesWithNullUser() {
        Map<String, Object> context = Map.of("k", "v");

        ((AppLogger) logger).info("app.starting", context);

        RecordingLogger.Event event = logger.only("app.starting");
        assertEquals("INFO", event.level());
        assertNull(event.userId());
        assertSame(context, event.context());
    }

    @Test
    void warnOverloadDelegatesWithNullUser() {
        ((AppLogger) logger).warn("film.not_found", Map.of());

        RecordingLogger.Event event = logger.only("film.not_found");
        assertEquals("WARN", event.level());
        assertNull(event.userId());
    }

    @Test
    void debugOverloadDelegatesWithNullUser() {
        ((AppLogger) logger).debug("cache.miss", Map.of());

        RecordingLogger.Event event = logger.only("cache.miss");
        assertEquals("DEBUG", event.level());
        assertNull(event.userId());
    }

    @Test
    void errorOverloadDelegatesWithNullUserAndKeepsCause() {
        RuntimeException cause = new RuntimeException("boom");

        ((AppLogger) logger).error("db.init.failed", Map.of(), cause);

        RecordingLogger.Event event = logger.only("db.init.failed");
        assertEquals("ERROR", event.level());
        assertNull(event.userId());
        assertSame(cause, event.cause());
    }
}
