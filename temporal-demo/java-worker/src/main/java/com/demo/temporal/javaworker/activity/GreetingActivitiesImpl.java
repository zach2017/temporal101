package com.demo.temporal.javaworker.activity;

import java.net.InetAddress;
import java.time.Instant;

public class GreetingActivitiesImpl implements GreetingActivities {

    @Override
    public String composeGreeting(String name) {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }

        return String.format(
                "Hello %s from Java Worker! [host=%s, jdk=%s, temporal-sdk=1.32.0, time=%s]",
                name,
                hostname,
                System.getProperty("java.version"),
                Instant.now().toString()
        );
    }
}
