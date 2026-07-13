package br.com.g9.energiai.backend.dto;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.*;
import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyAnalysisDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeEnergyAnalysisRequestUsingExpectedFieldNames() throws Exception {
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(250.5, true, 8, PropertyType.APARTAMENTO, 4);
        String json = objectMapper.writeValueAsString(request);
        assertEquals("{\"consumo_kwh\":250.5,\"uso_horario_pico\":true,\"quantidade_equipamentos\":8,\"tipo_imovel\":\"APARTAMENTO\",\"horas_alto_consumo\":4}", json);
    }

    @Test
    void shouldSerializeEnergyAnalysisResponseUsingExpectedFieldNames() throws Exception {
        EnergyAnalysisResponse response = new EnergyAnalysisResponse(1L, EnergyCategory.MODERADO, 0.87, 72, new BigDecimal("189.90"), List.of("Dica"), ClassificationSource.RULE_BASED);
        String json = objectMapper.writeValueAsString(response);
        assertEquals("{\"id\":1,\"categoria\":\"MODERADO\",\"probabilidade\":0.87,\"score\":72,\"custo_estimado_mensal\":189.90,\"recomendacoes\":[\"Dica\"],\"fonte_classificacao\":\"RULE_BASED\"}", json);
    }

    @Test
    void shouldSerializeEnergyAnalysisDetailResponseUsingExpectedFieldNames() throws Exception {
        EnergyAnalysisDetailResponse detail = new EnergyAnalysisDetailResponse(
                1L, 500.0, true, 10, PropertyType.CASA, 8, EnergyCategory.INEFICIENTE, 0.95, 95,
                new BigDecimal("375.00"), List.of("Dica"), ClassificationSource.RULE_BASED,
                LocalDateTime.of(2026, 7, 13, 15, 0, 0)
        );
        String json = objectMapper.writeValueAsString(detail);
        assertEquals("{\"id\":1,\"consumo_kwh\":500.0,\"uso_horario_pico\":true,\"quantidade_equipamentos\":10,\"tipo_imovel\":\"CASA\",\"horas_alto_consumo\":8,\"categoria\":\"INEFICIENTE\",\"probabilidade\":0.95,\"score\":95,\"custo_estimado_mensal\":375.00,\"recomendacoes\":[\"Dica\"],\"fonte_classificacao\":\"RULE_BASED\",\"criado_em\":\"2026-07-13T15:00:00\"}", json);
    }
}
