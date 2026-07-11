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
            API REST para análise de consumo energético, classificação
            de eficiência, cálculo de custo e geração de recomendações.
            """
    )
)
public class OpenApiConfig {
}
