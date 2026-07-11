package br.com.g9.energiai.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "EnergIAI API",
        version = "v1",
        description = """
            API REST para analise de consumo energetico, classificacao
            de eficiencia, calculo de custo e geracao de recomendacoes.
            """
    )
)
public class OpenApiConfig {
}
