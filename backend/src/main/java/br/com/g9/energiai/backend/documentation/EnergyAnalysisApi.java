package br.com.g9.energiai.backend.documentation;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisListResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;

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
                            schema = @Schema(implementation = EnergyAnalysisResponse.class),
                            examples = @ExampleObject(
                                    value = """
                        {
                          "id": 1,
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
    ResponseEntity<EnergyAnalysisResponse> createAnalysis(EnergyAnalysisRequest request);

    @Operation(
            summary = "Listar histórico de análises",
            description = "Retorna uma página do histórico de análises, da mais recente para a mais antiga. "
                    + "Aceita os parâmetros page e size; por padrão, retorna a página 0 com 20 itens, "
                    + "ordenados por createdAt em ordem decrescente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de análises recuperada com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EnergyAnalysisListResponse.class),
                            examples = @ExampleObject(
                                    value = """
                        {
                          "analises": [
                            {
                              "id": 2,
                              "categoria": "INEFICIENTE",
                              "probabilidade": 0.95,
                              "score": 95,
                              "custo_estimado_mensal": 315.00,
                              "criado_em": "2026-07-13T18:30:00"
                            }
                          ],
                          "pagina_atual": 0,
                          "tamanho_pagina": 20,
                          "total_elementos": 2,
                          "total_paginas": 1
                        }
                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno ao buscar o histórico",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EnergyAnalysisListResponse> listAnalyses(@ParameterObject Pageable pageable);
}
