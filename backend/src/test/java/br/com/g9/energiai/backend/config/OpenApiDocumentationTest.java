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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Deve gerar a documentação OpenAPI com metadados, media types e schemas esperados")
    void shouldGenerateOpenApiDocumentation() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.info.title").value("EnergIAI API"))
            .andExpect(jsonPath("$.info.version").value("v1"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200']").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['400']").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['500']").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.requestBody.content['application/json'].schema['$ref']")
                .value("#/components/schemas/EnergyAnalysisRequest"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/EnergyAnalysisResponse"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['400'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['500'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisRequest").exists())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse").exists())
            .andExpect(jsonPath("$.components.schemas.ApiErrorResponse").exists())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisRequest.properties.consumo_kwh.example").value("420"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.probabilidade.type").value("number"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.custo_estimado_mensal.type").value("number"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisRequest.properties.tipo_imovel.enum").isArray())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.categoria.enum").isArray())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.fonte_classificacao.enum").isArray())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200'].content['application/json'].example.probabilidade")
                .value(0.95))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200'].content['application/json'].example.custo_estimado_mensal")
                .value(315.0))
            .andExpect(content().string(containsString("\"consumo_kwh\"")))
            .andExpect(content().string(containsString("\"custo_estimado_mensal\"")))
            .andExpect(content().string(containsString("\"fonte_classificacao\"")));
    }

    @Test
    @DisplayName("Deve disponibilizar o Swagger UI no caminho público com context-path")
    void shouldServeSwaggerUi() throws Exception {
        mockMvc.perform(get("/api/v1/swagger-ui/index.html").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
