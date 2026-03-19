package com.example.servicea;

import com.example.common.JsonUtil;  // ← from common-lib
import com.example.common.User;       // ← from common-lib
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service-A: Creates users and serializes them to JSON.
 * Demonstrates using the shared common-lib dependency.
 */
public class ServiceA {

    private static final Logger log = LoggerFactory.getLogger(ServiceA.class);

    public static void main(String[] args) {
        log.info("=== Service-A starting (Java {}) ===",
                 Runtime.version());

        // Use shared model + shared utility from common-lib
        User user = User.create("u-001", "Alice Johnson", "Alice@Example.COM");
        String json = JsonUtil.toJson(user);

        log.info("Created user:\n{}", json);

        // Round-trip: JSON → User record
        User parsed = JsonUtil.fromJson(json, User.class);
        log.info("Parsed back: {}", parsed);
        log.info("Emails match: {}", user.email().equals(parsed.email()));

        log.info("=== Service-A finished ===");
    }
}
