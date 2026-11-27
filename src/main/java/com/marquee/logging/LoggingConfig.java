// src/main/java/com/marquee/LoggingConfig.java
package com.marquee.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingConfig {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingConfig.class);

    private LoggingConfig() {}


    public static void init() {
        // If you use Logback, the configuration is picked up from
        // src/main/resources/logback.xml. No extra code is needed here.
        // For JUL you would add a FileHandler / ConsoleHandler here.
        LOG.info("Logging initialised");
    }
}