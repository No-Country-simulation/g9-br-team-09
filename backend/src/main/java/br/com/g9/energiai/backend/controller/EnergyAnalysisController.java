package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.service.EnergyAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analise-energetica")
@RequiredArgsConstructor
@Tag(
    name = "Análise energética",
    description = "Operações para análise do perfil de consumo energético"
)
public class EnergyAnalysisController {

    private final EnergyAnalysisService energyAnalysisService;

    @PostMapping
    @Operation(
        summary = "Criar análise energética",
        description = """
            Valida os dados de consumo, calcula o custo mensal estimado,
            classifica o perfil energético e retorna recomendações.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Análise realizada com sucesso",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = EnergyAnalysisResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "categoria": "INEFICIENTE",
                          "probabilidade": 0.95,
                          "score": 95,
                          "custo_estimado_mensal": 315.00,
                          "recomendacoes": [
                            "Reduzir o uso de equipamentos durante horários de pico."
                          ],
                          "fonte_classificacao": "RULE_BASED"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos, enum inválido, tipo inválido ou JSON malformado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro interno inesperado, sem exposição de stack trace",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiErrorResponse.class)
            )
        )
    })
    public ResponseEntity<EnergyAnalysisResponse> createAnalysis(@RequestBody @Valid EnergyAnalysisRequest request) {
        EnergyAnalysisResponse response = energyAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }
}
