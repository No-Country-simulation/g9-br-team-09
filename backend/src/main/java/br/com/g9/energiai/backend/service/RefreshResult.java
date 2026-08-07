package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.response.AuthenticationResponse;

public record RefreshResult(AuthenticationResponse response, IssuedRefreshToken refreshToken) {
}
