package br.com.g9.energiai.backend.documentation;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDetailResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisListResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(
        name = "Análise energética",
        description = "Operações para análise do perfil de consumo energético"
)
public interface EnergyAnalysisApi {

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
                            schema = @Schema(implementation = EnergyAnalysisResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EnergyAnalysisResponse> createAnalysis(EnergyAnalysisRequest request);

    @Operation(
            summary = "Listar histórico de análises",
            description = "Retorna uma página do histórico de análises, da mais recente para a mais antiga."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista recuperada com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EnergyAnalysisListResponse.class)
                    )
            )
    })
    ResponseEntity<EnergyAnalysisListResponse> listAnalyses(@ParameterObject Pageable pageable);

    @Operation(
            summary = "Buscar análise por ID",
            description = "Recupera os detalhes completos de uma análise energética específica."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Análise encontrada com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EnergyAnalysisDetailResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Análise não encontrada",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EnergyAnalysisDetailResponse> getAnalysisById(
            @Parameter(description = "Identificador único da análise", example = "1") Long id
    );
}
