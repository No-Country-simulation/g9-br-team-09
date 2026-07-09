package br.com.g9.energiai.backend.dto;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisListResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisSummaryResponse;
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
        EnergyAnalysisRequest request = new EnergyAnalysisRequest(
            250.5,
            true,
            8,
            PropertyType.APARTAMENTO,
            4
        );

        String json = objectMapper.writeValueAsString(request);

        assertEquals(
            "{\"consumoKwh\":250.5,\"usoHorarioPico\":true,\"quantidadeEquipamentos\":8,"
                + "\"tipoImovel\":\"APARTAMENTO\",\"horasAltoConsumo\":4}",
            json
        );
    }

    @Test
    void shouldSerializeEnergyAnalysisResponseUsingExpectedFieldNames() throws Exception {
        EnergyAnalysisResponse response = new EnergyAnalysisResponse(
            EnergyCategory.MODERADO,
            0.87,
            72,
            new BigDecimal("189.90"),
            List.of("Trocar lampadas", "Reduzir uso no horario de pico"),
            ClassificationSource.RULE_BASED
        );

        String json = objectMapper.writeValueAsString(response);

        assertEquals(
            "{\"categoria\":\"MODERADO\",\"probabilidade\":0.87,\"score\":72,"
                + "\"custoEstimadoMensal\":189.90,\"recomendacoes\":[\"Trocar lampadas\","
                + "\"Reduzir uso no horario de pico\"],\"fonteClassificacao\":\"RULE_BASED\"}",
            json
        );
    }

    @Test
    void shouldSerializeEnergyAnalysisSummaryResponseUsingExpectedFieldNames() throws Exception {
        EnergyAnalysisSummaryResponse summary = new EnergyAnalysisSummaryResponse(
            10L,
            EnergyCategory.EFICIENTE,
            0.95,
            91,
            new BigDecimal("120.00"),
            LocalDateTime.of(2026, 7, 9, 14, 30, 0)
        );

        String json = objectMapper.writeValueAsString(summary);

        assertEquals(
            "{\"id\":10,\"categoria\":\"EFICIENTE\",\"probabilidade\":0.95,\"score\":91,"
                + "\"custoEstimadoMensal\":120.00,\"criadoEm\":\"2026-07-09T14:30:00\"}",
            json
        );
    }

    @Test
    void shouldSerializeEnergyAnalysisListResponseUsingExpectedFieldNames() throws Exception {
        EnergyAnalysisListResponse listResponse = new EnergyAnalysisListResponse(
            List.of(
                new EnergyAnalysisSummaryResponse(
                    10L,
                    EnergyCategory.EFICIENTE,
                    0.95,
                    91,
                    new BigDecimal("120.00"),
                    LocalDateTime.of(2026, 7, 9, 14, 30, 0)
                )
            )
        );

        String json = objectMapper.writeValueAsString(listResponse);

        assertEquals(
            "{\"analises\":[{\"id\":10,\"categoria\":\"EFICIENTE\",\"probabilidade\":0.95,"
                + "\"score\":91,\"custoEstimadoMensal\":120.00,"
                + "\"criadoEm\":\"2026-07-09T14:30:00\"}]}",
            json
        );
    }

    @Test
    void shouldSerializeApiErrorResponseUsingExpectedFieldNames() throws Exception {
        ApiErrorResponse errorResponse = new ApiErrorResponse(
            LocalDateTime.of(2026, 7, 9, 15, 0, 0),
            400,
            "Bad Request",
            "Payload invalido"
        );

        String json = objectMapper.writeValueAsString(errorResponse);

        assertEquals(
            "{\"timestamp\":\"2026-07-09T15:00:00\",\"status\":400,"
                + "\"error\":\"Bad Request\",\"message\":\"Payload invalido\"}",
            json
        );
    }
}
