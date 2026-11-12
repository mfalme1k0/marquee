package com.marquee;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Keeps CI green on an empty scaffold. Delete once real tests exist. */
class MainSmokeTest {
    @Test
    void mainClassLoads() {
        assertNotNull(Main.class);
    }
}
