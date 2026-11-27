package com.marquee.logging;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for {@link AppLogger} that records every call instead of writing
 * to a file or database. Lets service and handler tests assert on the events
 * they emit.
 */
public final class RecordingLogger implements AppLogger {

    public record Event(
            String level,
            Long userId,
            String event,
            Map<String, ?> context,
            Throwable cause) {}

    private final List<Event> events = new CopyOnWriteArrayList<>();

    @Override
    public void info(Long userId, String event, Map<String, ?> context) {
        events.add(new Event("INFO", userId, event, context, null));
    }

    @Override
    public void warn(Long userId, String event, Map<String, ?> context) {
        events.add(new Event("WARN", userId, event, context, null));
    }

    @Override
    public void debug(Long userId, String event, Map<String, ?> context) {
        events.add(new Event("DEBUG", userId, event, context, null));
    }

    @Override
    public void error(Long userId, String event, Map<String, ?> context, Throwable cause) {
        events.add(new Event("ERROR", userId, event, context, cause));
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public List<String> eventNames() {
        return events.stream().map(Event::event).toList();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    /** The single event with this name; fails if there are none or several. */
    public Event only(String eventName) {
        List<Event> matches = events.stream()
                .filter(e -> e.event().equals(eventName))
                .toList();
        if (matches.size() != 1) {
            throw new AssertionError("Expected exactly one '" + eventName
                    + "' event but found " + matches.size() + " in " + eventNames());
        }
        return matches.get(0);
    }

    public void clear() {
        events.clear();
    }
}
