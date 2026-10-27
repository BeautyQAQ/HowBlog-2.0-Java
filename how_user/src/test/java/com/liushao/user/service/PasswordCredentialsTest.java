package com.liushao.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordCredentialsTest {
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void directBcryptAcceptsExactly72BytesAndRejectsLongerCandidates() {
        String password = "汉".repeat(24);
        String encoded = encoder.encode(password);

        assertTrue(PasswordCredentials.matches(password, encoded, encoder));
        assertFalse(PasswordCredentials.matches(password + "a", encoded, encoder));
        assertFalse(PasswordCredentials.matches(password + "b", encoded, encoder));
    }

    @Test
    void legacyLongPlaintextMustMatchFullyBeforeAndAfterUpgrade() {
        String password = "汉".repeat(24) + "a";
        String collision = "汉".repeat(24) + "b";

        assertTrue(PasswordCredentials.matches(password, password, encoder));
        assertFalse(PasswordCredentials.matches(collision, password, encoder));

        String encoded = PasswordCredentials.upgradeLegacy(password, encoder);

        assertTrue(encoded.startsWith("{bcrypt-sha256}"));
        assertTrue(PasswordCredentials.isEncoded(encoded));
        assertTrue(PasswordCredentials.matches(password, encoded, encoder));
        assertFalse(PasswordCredentials.matches(collision, encoded, encoder));
        assertFalse(PasswordCredentials.matches("汉".repeat(24), encoded, encoder));
    }

    @Test
    void legacyShortPasswordsRemainDirectBcryptAndMalformedHashesFailClosed() {
        String encoded = PasswordCredentials.upgradeLegacy("legacy-password", encoder);

        assertTrue(encoded.startsWith("$2"));
        assertTrue(PasswordCredentials.matches("legacy-password", encoded, encoder));
        assertFalse(PasswordCredentials.matches(null, encoded, encoder));
        assertFalse(PasswordCredentials.matches("legacy-password", "{bcrypt-sha256}$2a$bad", encoder));
    }
}
