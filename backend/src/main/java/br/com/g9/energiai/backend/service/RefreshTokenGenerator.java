package br.com.g9.energiai.backend.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshTokenGenerator {

    static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public RefreshTokenGenerator() {
        this(new SecureRandom());
    }

    RefreshTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
