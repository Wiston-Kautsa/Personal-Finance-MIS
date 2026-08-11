package com.wk.pfmis.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordSecurityTest {

    @Test
    void hashVerifiesMatchingPasswordOnly() {
        PasswordSecurity.PasswordRecord record = PasswordSecurity.hash("ValidPass123");

        assertTrue(PasswordSecurity.verify("ValidPass123", record.hash(), record.salt(), record.iterations()));
        assertFalse(PasswordSecurity.verify("WrongPass123", record.hash(), record.salt(), record.iterations()));
    }

    @Test
    void hashUsesRandomSalt() {
        PasswordSecurity.PasswordRecord first = PasswordSecurity.hash("ValidPass123");
        PasswordSecurity.PasswordRecord second = PasswordSecurity.hash("ValidPass123");

        assertNotEquals(first.salt(), second.salt());
        assertNotEquals(first.hash(), second.hash());
    }

    @Test
    void weakPasswordsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PasswordSecurity.hash("short"));
        assertThrows(IllegalArgumentException.class, () -> PasswordSecurity.hash("lowercase123"));
        assertThrows(IllegalArgumentException.class, () -> PasswordSecurity.hash("UPPERCASE123"));
        assertThrows(IllegalArgumentException.class, () -> PasswordSecurity.hash("NoDigitsHere"));
    }

    @Test
    void oldIterationCountsNeedRehash() {
        assertTrue(PasswordSecurity.needsRehash(PasswordSecurity.DEFAULT_ITERATIONS - 1));
        assertFalse(PasswordSecurity.needsRehash(PasswordSecurity.DEFAULT_ITERATIONS));
    }
}
