package com.hostmonitor.core;

public class MonitorCore {

    public String greet(String name) {
        return "Hello, " + name + "! Welcome to HostMonitor.";
    }

    public static void main(String[] args) {
        MonitorCore core = new MonitorCore();
        System.out.println(core.greet("HostMonitor"));
    }
}
