package com.marquee.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LogEntryTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 26, 10, 15, 30);

    @Test
    void newEventHasNoIdBecauseTheDatabaseAssignsIt() {
        LogEntry entry = LogEntry.newEvent(WHEN, "INFO", "film.created", "{\"film_id\":1}", 7L);

        assertNull(entry.id());
    }

    @Test
    void newEventCarriesAllSuppliedFields() {
        LogEntry entry = LogEntry.newEvent(WHEN, "WARN", "film.not_found", "{\"film_id\":9}", 7L);

        assertEquals(WHEN, entry.occurredAt());
        assertEquals("WARN", entry.level());
        assertEquals("film.not_found", entry.event());
        assertEquals("{\"film_id\":9}", entry.contextJson());
        assertEquals(7L, entry.userId());
    }

    @Test
    void newEventAllowsAnonymousUser() {
        LogEntry entry = LogEntry.newEvent(WHEN, "INFO", "app.starting", "{}", null);

        assertNull(entry.userId());
    }

    @Test
    void entriesWithSameValuesAreEqual() {
        assertEquals(
                LogEntry.newEvent(WHEN, "INFO", "app.starting", "{}", null),
                LogEntry.newEvent(WHEN, "INFO", "app.starting", "{}", null)
        );
    }
}
