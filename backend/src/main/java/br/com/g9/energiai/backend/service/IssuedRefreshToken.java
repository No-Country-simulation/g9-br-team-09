package br.com.g9.energiai.backend.service;

import java.time.Duration;

public final class IssuedRefreshToken {

    private final String rawToken;
    private final Duration maxAge;

    public IssuedRefreshToken(String rawToken, Duration maxAge) {
        this.rawToken = rawToken;
        this.maxAge = maxAge;
    }

    public String rawToken() {
        return rawToken;
    }

    public Duration maxAge() {
        return maxAge;
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[rawToken=[REDACTED], maxAge=" + maxAge + "]";
    }
}
