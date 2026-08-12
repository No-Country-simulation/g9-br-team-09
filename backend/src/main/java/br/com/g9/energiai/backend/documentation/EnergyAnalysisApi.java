package br.com.g9.energiai.backend.documentation;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.ApiErrorResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDashboardResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDetailResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisListResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Análise energética", description = "Operações para análise do perfil de consumo energético")
public interface EnergyAnalysisApi {

    @Operation(
            summary = "Criar análise energética",
            description = """
                    Exige um Bearer JWT válido. Valida os dados de consumo, calcula o custo mensal estimado,
                    classifica o perfil energético e retorna recomendações.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
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
                          "probabilidade": 0.75,
                          "score": 95,
                          "custo_estimado_mensal": 315.00,
                          "recomendacoes": [
                            "Reduzir o uso de equipamentos durante horários de pico."
                          ],
                          "fonte_classificacao": "RULE_BASED_FALLBACK"
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
                    responseCode = "401",
                    description = "Bearer JWT ausente, inválido ou sem usuário autorizado",
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
            description = "Exige um Bearer JWT válido. Retorna uma página do histórico de análises do usuário "
                    + "autenticado, da mais recente para a mais antiga. Análises de outros usuários e registros "
                    + "legados sem proprietário nunca são retornados. "
                    + "Aceita os parâmetros page e size; por padrão, retorna a página 0 com 20 itens, "
                    + "ordenados por createdAt em ordem decrescente.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de análises do usuário autenticado recuperada com sucesso",
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
                    responseCode = "401",
                    description = "Bearer JWT ausente, inválido, expirado ou sem usuário autorizado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
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

    @Operation(
            summary = "Buscar análise por ID",
            description = "Exige um Bearer JWT válido. Retorna a análise somente quando pertencer ao usuário "
                    + "autenticado. IDs inexistentes e IDs de outros usuários retornam a mesma resposta 404, "
                    + "impedindo enumeração de identificadores ou descoberta de dados de terceiros.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Análise encontrada com sucesso",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EnergyAnalysisDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Bearer JWT ausente, inválido, expirado ou sem usuário autorizado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Análise não encontrada (Inclui IDs inexistentes e de outros usuários)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    ResponseEntity<EnergyAnalysisDetailResponse> getAnalysisById(
            @Parameter(
                    name = "id",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Identificador único da análise",
                    example = "1"
            )
            Long id
    );

    @Operation(
            summary = "Obter resumo estatístico das análises",
            description = "Exige um Bearer JWT válido. Retorna os indicadores agregados do usuário autenticado. "
                    + "Usuários sem análises recebem valores neutros (zerados) com status 200.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Resumo estatístico do usuário autenticado recuperado com sucesso",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EnergyAnalysisDashboardResponse.class),
                            examples = @ExampleObject(
                                    value = """
                        {
                          "total_analises": 35,
                          "media_consumo_kwh": 382.5,
                          "media_custo_mensal": 286.87,
                          "total_eficiente": 8,
                          "total_moderado": 16,
                          "total_ineficiente": 11
                        }
                        """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Bearer JWT ausente, inválido, expirado ou sem usuário autorizado",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno ao gerar o resumo estatístico",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)
                    )
            )
    })
    ResponseEntity<EnergyAnalysisDashboardResponse> getDashboardSummary();
}
