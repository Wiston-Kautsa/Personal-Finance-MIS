package com.wk.pfmis.controllers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UiAlertsTest {
    @Test
    void rootMessageUsesDeepestCause() {
        RuntimeException exception = new RuntimeException(
                "Failed to add account",
                new IllegalStateException("Failed to execute SQL", new RuntimeException("no such column: is_deleted"))
        );

        assertEquals("no such column: is_deleted", UiAlerts.rootMessage(exception));
    }

    @Test
    void rootMessageRedactsKnownSecrets() {
        String fakeKey = "sk-" + "proj-" + "exampleSecretValue1234567890";
        RuntimeException exception = new RuntimeException("api_key=" + fakeKey);

        String message = UiAlerts.rootMessage(exception);

        assertFalse(message.contains(fakeKey));
    }
}
