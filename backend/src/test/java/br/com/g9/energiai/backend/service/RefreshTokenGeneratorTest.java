package br.com.g9.energiai.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenGeneratorTest {

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

    @Test
    void shouldGenerateDifferentUrlSafeTokensWithAtLeast256Bits() {
        String first = generator.generate();
        String second = generator.generate();

        assertNotEquals(first, second);
        assertFalse(first.contains("="));
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
        assertEquals(32, Base64.getUrlDecoder().decode(first).length);
    }
}
