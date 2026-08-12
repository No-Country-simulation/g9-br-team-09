package br.com.g9.energiai.backend.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CsrfCookieService {

    private final CsrfTokenRepository csrfTokenRepository;

    public void issue(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
        }

        csrfTokenRepository.saveToken(token, request, response);
        response.setHeader(token.getHeaderName(), token.getToken());
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        csrfTokenRepository.saveToken(null, request, response);
    }
}
