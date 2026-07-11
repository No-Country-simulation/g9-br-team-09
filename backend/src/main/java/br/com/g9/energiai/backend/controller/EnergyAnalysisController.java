package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.service.EnergyAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analise-energetica")
@RequiredArgsConstructor
@Tag(
    name = "Analise energetica",
    description = "Operacoes para analise do perfil de consumo energetico"
)
public class EnergyAnalysisController {

    private final EnergyAnalysisService energyAnalysisService;

    @PostMapping
    @Operation(
        summary = "Criar analise energetica",
        description = """
            Valida os dados de consumo, calcula o custo mensal estimado,
            classifica o perfil energetico e retorna recomendacoes.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Analise realizada com sucesso",
            content = @Content(schema = @Schema(implementation = EnergyAnalysisResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Dados invalidos, enum invalido, tipo invalido ou JSON malformado",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro interno inesperado, sem exposicao de stack trace",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
    })
    public ResponseEntity<EnergyAnalysisResponse> createAnalysis(@RequestBody @Valid EnergyAnalysisRequest request) {
        EnergyAnalysisResponse response = energyAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }
}
