package br.com.g9.energiai.backend.config;

import br.com.g9.energiai.backend.support.LocalProfileTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@LocalProfileTest
class WebConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenProperties refreshTokenProperties;

    @Test
    @DisplayName("Deve permitir cookie sem Secure somente no profile local")
    void shouldDisableSecureCookieForLocalHttp() {
        assertFalse(refreshTokenProperties.cookieSecure());
    }

    @Test
    @DisplayName("Deve permitir CORS para origem autorizada http://localhost:5173 e não retornar wildcard")
    void shouldAllowCorsForPort5173() throws Exception {
        mockMvc.perform(options("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Origin", not(containsString("*"))))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Deve permitir CORS para origem autorizada http://localhost:3000 e não retornar wildcard")
    void shouldAllowCorsForPort3000() throws Exception {
        mockMvc.perform(options("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Origin", not(containsString("*"))))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Deve rejeitar CORS para origem não autorizada")
    void shouldDenyCorsForUnauthorizedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Origin", "https://origem-nao-permitida.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Deve validar a requisição de preflight OPTIONS com sucesso")
    void shouldAllowPreflightOptionsRequest() throws Exception {
        mockMvc.perform(options("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Deve permitir e expor X-XSRF-TOKEN no fluxo com credenciais")
    void shouldAllowCsrfHeaderForRefreshPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/auth/refresh")
                        .contextPath("/api/v1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-XSRF-TOKEN")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-XSRF-TOKEN")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

}
