package br.com.g9.energiai.backend.dto.response;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "EnergyAnalysisDetailResponse", description = "Detalhes completos de uma análise energética")
public record EnergyAnalysisDetailResponse(
        Long id,
        @JsonProperty("consumo_kwh") Double consumoKwh,
        @JsonProperty("uso_horario_pico") Boolean usoHorarioPico,
        @JsonProperty("quantidade_equipamentos") Integer quantidadeEquipamentos,
        @JsonProperty("tipo_imovel") PropertyType tipoImovel,
        @JsonProperty("horas_alto_consumo") Integer horasAltoConsumo,
        EnergyCategory categoria,
        Double probabilidade,
        Integer score,
        @JsonProperty("custo_estimado_mensal") BigDecimal custoEstimadoMensal,
        List<String> recomendacoes,
        @JsonProperty("fonte_classificacao") ClassificationSource fonteClassificacao,
        @JsonProperty("criado_em") LocalDateTime criadoEm
) {}
