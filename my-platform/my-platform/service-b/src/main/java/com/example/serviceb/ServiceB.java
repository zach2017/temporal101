package com.example.serviceb;

import com.example.common.JsonUtil;  // ← from common-lib
import com.example.common.User;       // ← from common-lib
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service-B: Receives user JSON and processes it.
 * Also uses the shared common-lib, proving both services
 * can depend on the same internal module.
 */
public class ServiceB {

    private static final Logger log = LoggerFactory.getLogger(ServiceB.class);

    public static void main(String[] args) {
        log.info("=== Service-B starting (Java {}) ===",
                 Runtime.version());

        // Simulate receiving JSON payloads from Service-A
        List<String> incomingPayloads = List.of(
            JsonUtil.toJson(User.create("u-001", "Alice Johnson", "alice@example.com")),
            JsonUtil.toJson(User.create("u-002", "Bob Smith", "BOB@EXAMPLE.COM")),
            JsonUtil.toJson(User.create("u-003", "Charlie Lee", "Charlie@Example.Org"))
        );

        log.info("Processing {} user payloads...", incomingPayloads.size());

        for (String payload : incomingPayloads) {
            // Deserialize using the shared utility
            User user = JsonUtil.fromJson(payload, User.class);
            processUser(user);
        }

        log.info("=== Service-B finished ===");
    }

    private static void processUser(User user) {
        // Java 21 pattern matching with switch
        String tier = switch (user.email().split("@")[1]) {
            case "example.com" -> "INTERNAL";
            case "example.org" -> "PARTNER";
            default            -> "EXTERNAL";
        };
        log.info("  User {} ({}) → tier: {}", user.name(), user.email(), tier);
    }
}
