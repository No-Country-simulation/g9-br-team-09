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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "CORS_ALLOWED_ORIGINS=https://portal.example, https://administracao.example")
@AutoConfigureMockMvc
@LocalProfileTest
class WebConfigExternalOriginsCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Deve permitir múltiplas origens fornecidas por variável externa")
    void shouldAllowMultipleExternallyConfiguredOrigins() throws Exception {
        for (String origin : new String[]{"https://portal.example", "https://administracao.example"}) {
            mockMvc.perform(options("/api/v1/analise-energetica")
                            .contextPath("/api/v1")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Origin", not(containsString("*"))));
        }
    }

    @Test
    @DisplayName("Não deve autorizar uma origem local ausente da configuração externa")
    void shouldDenyOriginMissingFromExternalConfiguration() throws Exception {
        mockMvc.perform(options("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
