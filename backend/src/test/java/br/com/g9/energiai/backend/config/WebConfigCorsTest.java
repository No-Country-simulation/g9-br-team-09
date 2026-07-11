package br.com.g9.energiai.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WebConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Deve permitir CORS para origem autorizada http://localhost:5173 e não retornar wildcard")
    void shouldAllowCorsForPort5173() throws Exception {
        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Origin", not(containsString("*"))))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Deve permitir CORS para origem autorizada http://localhost:3000 e não retornar wildcard")
    void shouldAllowCorsForPort3000() throws Exception {
        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Origin", not(containsString("*"))))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Deve rejeitar CORS para origem não autorizada")
    void shouldDenyCorsForUnauthorizedOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/analise-energetica")
                        .contextPath("/api/v1")
                        .header("Origin", "https://origem-nao-permitida.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
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

    private String validRequestJson() {
        return """
            {
              "consumo_kwh": 500,
              "uso_horario_pico": true,
              "quantidade_equipamentos": 10,
              "tipo_imovel": "CASA",
              "horas_alto_consumo": 8
            }
            """;
    }
}
