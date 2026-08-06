package br.com.g9.energiai.backend.exception;

public class RefreshTokenAuthenticationException extends RuntimeException {

    public static final String GENERIC_MESSAGE = "Não foi possível renovar a sessão";

    private final boolean clearRefreshCookie;

    public RefreshTokenAuthenticationException(boolean clearRefreshCookie) {
        super(GENERIC_MESSAGE);
        this.clearRefreshCookie = clearRefreshCookie;
    }

    public boolean shouldClearRefreshCookie() {
        return clearRefreshCookie;
    }
}
