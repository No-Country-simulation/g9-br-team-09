package br.com.g9.energiai.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenHasherTest {

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    @Test
    void shouldHashDeterministicallyWithoutPreservingRawToken() {
        String rawToken = "token-opaco-de-teste";

        String first = hasher.hash(rawToken);
        String second = hasher.hash(rawToken);

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(rawToken, first);
        assertEquals("f8b7d8c7d05be12aa4425047b404a9893b645b35e9ea5dda2f9759bb263b812b", first);
    }
}
