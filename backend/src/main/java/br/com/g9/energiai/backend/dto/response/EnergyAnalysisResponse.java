package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "EnergyAnalysisResponse", description = "Resultado da analise energetica")
public record EnergyAnalysisResponse(
    @Schema(
        description = "Categoria final de eficiencia energetica.",
        example = "INEFICIENTE",
        allowableValues = {"EFICIENTE", "MODERADO", "INEFICIENTE"}
    )
    EnergyCategory categoria,

    @Schema(description = "Probabilidade associada a classificacao calculada.", example = "0.95")
    Double probabilidade,

    @Schema(description = "Score numerico utilizado na classificacao do perfil.", example = "95")
    Integer score,

    @JsonProperty("custo_estimado_mensal")
    @Schema(description = "Custo mensal estimado da energia em reais.", example = "315.00")
    BigDecimal custoEstimadoMensal,

    @ArraySchema(
        schema = @Schema(
            description = "Recomendacao gerada para melhorar o consumo energetico.",
            example = "Reduzir o uso de equipamentos durante horarios de pico."
        )
    )
    List<String> recomendacoes,

    @JsonProperty("fonte_classificacao")
    @Schema(
        description = "Origem da estrategia de classificacao utilizada.",
        example = "RULE_BASED",
        allowableValues = {"RULE_BASED", "ML_MODEL", "RULE_BASED_FALLBACK"}
    )
    ClassificationSource fonteClassificacao
) {
}
