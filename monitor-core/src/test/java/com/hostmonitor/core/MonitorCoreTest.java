package com.hostmonitor.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonitorCoreTest {

    @Test
    void testGreet() {
        MonitorCore core = new MonitorCore();
        assertEquals("Hello, World! Welcome to HostMonitor.", core.greet("World"));
    }
}
