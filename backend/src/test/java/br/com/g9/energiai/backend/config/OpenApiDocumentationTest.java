package br.com.g9.energiai.backend.config;

import br.com.g9.energiai.backend.support.LocalProfileTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "server.forward-headers-strategy=framework")
@AutoConfigureMockMvc
@LocalProfileTest
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
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['500']").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.security[0].bearerAuth").isArray())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.requestBody.content['application/json'].schema['$ref']")
                .value("#/components/schemas/EnergyAnalysisRequest"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/EnergyAnalysisResponse"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['400'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['401'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['500'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200'].content['application/json']").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['400'].content['application/json']").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['401'].content['application/json']").exists())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['500'].content['application/json']").exists())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisRequest").exists())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse").exists())
            .andExpect(jsonPath("$.components.schemas.ApiErrorResponse").exists())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisRequest.properties.consumo_kwh.example").value("420"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.id.type").value("integer"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.id.format").value("int64"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.id.example").value("1"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.probabilidade.type").value("number"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.custo_estimado_mensal.type").value("number"))
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisRequest.properties.tipo_imovel.enum").isArray())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.categoria.enum").isArray())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.fonte_classificacao.enum").isArray())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisRequest.properties.user_id").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.EnergyAnalysisResponse.properties.user_id").doesNotExist())
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200'].content['application/json'].example.id")
                .value(1))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200'].content['application/json'].example.probabilidade")
                .value(0.75))
            .andExpect(jsonPath("$.paths['/analise-energetica'].post.responses['200'].content['application/json'].example.custo_estimado_mensal")
                .value(315.0))
            .andExpect(content().string(containsString("\"consumo_kwh\"")))
            .andExpect(content().string(containsString("\"custo_estimado_mensal\"")))
            .andExpect(content().string(containsString("\"fonte_classificacao\"")));
    }

    @Test
    @DisplayName("Deve documentar os contratos completos dos endpoints de autenticação")
    void shouldDocumentAuthenticationContracts() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.description",
                    containsString("Authorization: Bearer <access_token>")))
            .andExpect(jsonPath("$.paths['/auth/register'].post.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/auth/register'].post.requestBody.content['application/json'].schema['$ref']")
                    .value("#/components/schemas/UserRegistrationRequest"))
            .andExpect(jsonPath("$.paths['/auth/register'].post.responses['201'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/UserRegistrationResponse"))
            .andExpect(jsonPath("$.paths['/auth/register'].post.responses['400'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/register'].post.responses['409'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/register'].post.responses['415'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/register'].post.responses['500'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/auth/login'].post.requestBody.content['application/json'].schema['$ref']")
                    .value("#/components/schemas/UserLoginRequest"))
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses['200'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/AuthenticationResponse"))
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses['200'].headers['Set-Cookie'].description",
                    containsString("refresh_token")))
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses['200'].headers['X-XSRF-TOKEN']").exists())
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses['400'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses['401'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses['415'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/login'].post.responses['500'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.requestBody").doesNotExist())
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.parameters[0].name").value("refresh_token"))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.parameters[0].in").value("cookie"))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.parameters[0].required").value(true))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.parameters[1].name").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.parameters[1].in").value("header"))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.parameters[1].required").value(true))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['200'].headers['Set-Cookie'].description",
                    containsString("refresh_token")))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['200'].headers['X-XSRF-TOKEN']").exists())
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['200'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/AuthenticationResponse"))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['401'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['403'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/refresh'].post.responses['500'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/logout'].post.requestBody").doesNotExist())
            .andExpect(jsonPath("$.paths['/auth/logout'].post.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/auth/logout'].post.parameters[0].name").value("refresh_token"))
            .andExpect(jsonPath("$.paths['/auth/logout'].post.parameters[0].in").value("cookie"))
            .andExpect(jsonPath("$.paths['/auth/logout'].post.parameters[0].required").doesNotExist())
            .andExpect(jsonPath("$.paths['/auth/logout'].post.parameters[1].name").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.paths['/auth/logout'].post.parameters[1].in").value("header"))
            .andExpect(jsonPath("$.paths['/auth/logout'].post.parameters[1].required").value(true))
            .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['204'].headers['Set-Cookie'].description",
                    containsString("XSRF-TOKEN")))
            .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['403'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['500'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/me'].get.security[0].bearerAuth").isArray())
            .andExpect(jsonPath("$.paths['/auth/me'].get.responses['200'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/AuthenticatedUserResponse"))
            .andExpect(jsonPath("$.paths['/auth/me'].get.responses['401'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/me'].get.responses['403'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.paths['/auth/me'].get.responses['500'].content['application/json'].schema['$ref']")
                    .value("#/components/schemas/ApiErrorResponse"))
            .andExpect(jsonPath("$.components.schemas.AuthenticationResponse.properties.access_token.description",
                    containsString("JWT")))
            .andExpect(jsonPath("$.components.schemas.AuthenticationResponse.properties.token_type.example").value("Bearer"))
            .andExpect(jsonPath("$.components.schemas.AuthenticationResponse.properties.expires_in.example").value(900))
            .andExpect(jsonPath("$.components.schemas.UserLoginRequest.properties.senha.writeOnly").value(true))
            .andExpect(jsonPath("$.components.schemas.UserRegistrationRequest.properties.senha.writeOnly").value(true));
    }

    @Test
    @DisplayName("Deve disponibilizar o Swagger UI no caminho público com context-path")
    void shouldServeSwaggerUi() throws Exception {
        mockMvc.perform(get("/api/v1/swagger-ui/index.html").contextPath("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("Deve gerar o servidor OpenAPI com a origem HTTPS encaminhada pelo proxy")
    void shouldGenerateOpenApiServerUsingForwardedHttpsOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs")
                .contextPath("/api/v1")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "api.example.com")
                .header("X-Forwarded-Port", "443"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.servers[0].url").value("https://api.example.com/api/v1"))
            .andExpect(jsonPath("$.servers[0].url").value(containsString("https://")))
            .andExpect(jsonPath("$.servers[0].url").value(not(containsString("http://"))));
    }

    @Test
    @DisplayName("Deve usar HTTPS padrão quando o proxy não encaminha a porta")
    void shouldGenerateOpenApiServerUsingForwardedHttpsOriginWithoutPort() throws Exception {
        mockMvc.perform(get("/api/v1/v3/api-docs")
                .contextPath("/api/v1")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "api.example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.servers[0].url").value("https://api.example.com/api/v1"))
            .andExpect(jsonPath("$.servers[0].url").value(not(containsString("http://"))));
    }
}
