package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "EnergyAnalysisResponse", description = "Resultado da análise energética")
public record EnergyAnalysisResponse(
    @Schema(
        description = "Identificador único da análise energética persistida.",
        example = "1"
    )
    Long id,

    @Schema(
        description = "Categoria final de eficiência energética.",
        example = "INEFICIENTE",
        allowableValues = {"EFICIENTE", "MODERADO", "INEFICIENTE"}
    )
    EnergyCategory categoria,

    @Schema(description = "Probabilidade associada à classificação calculada.")
    Double probabilidade,

    @Schema(description = "Score numérico utilizado na classificação do perfil.", example = "95")
    Integer score,

    @JsonProperty("custo_estimado_mensal")
    @Schema(description = "Custo mensal estimado da energia em reais.")
    BigDecimal custoEstimadoMensal,

    @ArraySchema(
        schema = @Schema(
            description = "Recomendação gerada para melhorar o consumo energético.",
            example = "Reduzir o uso de equipamentos durante horários de pico."
        )
    )
    List<String> recomendacoes,

    @JsonProperty("fonte_classificacao")
    @Schema(
        description = "Origem da estratégia de classificação utilizada.",
        example = "RULE_BASED",
        allowableValues = {"RULE_BASED", "ML_MODEL", "RULE_BASED_FALLBACK"}
    )
    ClassificationSource fonteClassificacao
) {
}
