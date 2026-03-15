package com.demo.temporal.javaworker.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GreetingActivitiesImpl.
 *
 * Activities are plain Java methods, so these are straightforward
 * unit tests with no Temporal infrastructure needed.
 *
 * Test Flow:
 *   1. Create a GreetingActivitiesImpl instance directly
 *   2. Call composeGreeting() with various inputs
 *   3. Assert the output string format and content
 */
class GreetingActivitiesTest {

    private GreetingActivities activities;

    @BeforeEach
    void setUp() {
        activities = new GreetingActivitiesImpl();
    }

    @Test
    @DisplayName("composeGreeting returns greeting with the given name")
    void composeGreeting_containsName() {
        String result = activities.composeGreeting("Alice");

        assertTrue(result.contains("Hello Alice"), "Should greet with given name");
        assertTrue(result.contains("Java Worker"), "Should identify as Java Worker");
    }

    @Test
    @DisplayName("composeGreeting includes JDK version metadata")
    void composeGreeting_containsJdkVersion() {
        String result = activities.composeGreeting("Test");
        String expectedJdk = System.getProperty("java.version");

        assertTrue(result.contains("jdk=" + expectedJdk),
                "Should contain JDK version: " + expectedJdk);
    }

    @Test
    @DisplayName("composeGreeting includes temporal-sdk version")
    void composeGreeting_containsSdkVersion() {
        String result = activities.composeGreeting("Test");

        assertTrue(result.contains("temporal-sdk=1.32.0"),
                "Should contain SDK version");
    }

    @Test
    @DisplayName("composeGreeting includes timestamp")
    void composeGreeting_containsTimestamp() {
        String result = activities.composeGreeting("Test");

        // ISO-8601 timestamps contain 'T' between date and time
        assertTrue(result.contains("time="), "Should contain time field");
    }

    @Test
    @DisplayName("composeGreeting includes hostname")
    void composeGreeting_containsHostname() {
        String result = activities.composeGreeting("Test");

        assertTrue(result.contains("host="), "Should contain host field");
        // Hostname should not be empty
        assertFalse(result.contains("host=]"), "Hostname should not be empty");
    }

    @Test
    @DisplayName("composeGreeting handles empty name")
    void composeGreeting_emptyName() {
        String result = activities.composeGreeting("");

        assertTrue(result.startsWith("Hello "), "Should still start with Hello");
        assertTrue(result.contains("from Java Worker"), "Should identify worker");
    }

    @Test
    @DisplayName("composeGreeting handles special characters in name")
    void composeGreeting_specialCharacters() {
        String result = activities.composeGreeting("O'Brien <>&\"");

        assertTrue(result.contains("O'Brien <>&\""),
                "Should preserve special characters");
    }

    @Test
    @DisplayName("composeGreeting handles null name")
    void composeGreeting_nullName() {
        String result = activities.composeGreeting(null);

        assertTrue(result.contains("Hello null"),
                "Should handle null gracefully via String.format");
    }
}
