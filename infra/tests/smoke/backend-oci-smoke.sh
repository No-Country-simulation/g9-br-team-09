#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly VALID_PAYLOAD="${SCRIPT_DIR}/payload-valid.json"
readonly INVALID_PAYLOAD="${SCRIPT_DIR}/payload-invalid.json"

BASE_URL="${BASE_URL:-}"
REQUEST_TIMEOUT="${REQUEST_TIMEOUT:-15}"
EXPECTED_CLASSIFICATION_SOURCE="${EXPECTED_CLASSIFICATION_SOURCE:-}"
NON_EXISTENT_ID="${NON_EXISTENT_ID:-9223372036854775807}"
VALIDATED_ARTIFACT="${VALIDATED_ARTIFACT:-}"
TEMP_DIR=""
CURRENT_STAGE="inicialização"
INITIAL_TOTAL=""
TOTAL_AFTER_VALID=""
CREATED_ID=""
CREATED_CATEGORY=""
CREATED_PROBABILITY=""
CREATED_SCORE=""
CREATED_COST=""
CREATED_SOURCE=""

info() {
    printf '[INFO] %s\n' "$1"
}

pass() {
    printf '[PASS] %s\n' "$1"
}

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

cleanup() {
    if [[ -n "${TEMP_DIR}" && -d "${TEMP_DIR}" ]]; then
        rm -rf -- "${TEMP_DIR}"
    fi
}

on_unexpected_error() {
    local exit_code="$1"
    local line_number="$2"

    printf '[FAIL] Falha inesperada na etapa "%s" (linha %s, exit code %s).\n' \
        "${CURRENT_STAGE}" "${line_number}" "${exit_code}" >&2
}

trap cleanup EXIT
trap 'on_unexpected_error "$?" "$LINENO"' ERR

show_sanitized_excerpt() {
    local response_file="$1"
    local excerpt

    if jq -e 'type == "object"' "${response_file}" >/dev/null 2>&1; then
        excerpt="$(jq -c \
            '{status: (.status // null), error: (.error // null), body: "[REDACTED]"}' \
            "${response_file}" | head -c 512)"
    else
        excerpt='[NON_OBJECT_RESPONSE_REDACTED]'
    fi

    printf '[FAIL] Trecho sanitizado da resposta: %.512s\n' "${excerpt}" >&2
}

check_dependencies() {
    local dependency

    for dependency in bash curl jq mktemp date; do
        command -v "${dependency}" >/dev/null 2>&1 \
            || fail "Dependência obrigatória ausente: ${dependency}."
    done

    [[ -r "${VALID_PAYLOAD}" ]] || fail "Fixture válida não encontrada."
    [[ -r "${INVALID_PAYLOAD}" ]] || fail "Fixture inválida não encontrada."
    jq empty "${VALID_PAYLOAD}" >/dev/null 2>&1 || fail "Fixture válida contém JSON inválido."
    jq empty "${INVALID_PAYLOAD}" >/dev/null 2>&1 || fail "Fixture inválida contém JSON inválido."
}

validate_configuration() {
    [[ -n "${BASE_URL}" ]] || fail "BASE_URL é obrigatória."
    [[ "${BASE_URL}" =~ ^https?://[^/]+ ]] || fail "BASE_URL deve usar HTTP ou HTTPS e informar o host."
    [[ ! "${BASE_URL}" =~ [[:space:]] ]] || fail "BASE_URL não pode conter espaços."
    [[ "${BASE_URL}" != *"@"* ]] || fail "BASE_URL não pode conter credenciais."
    [[ "${BASE_URL}" != *"?"* && "${BASE_URL}" != *"#"* ]] \
        || fail "BASE_URL não pode conter query string ou fragmento."

    while [[ "${BASE_URL}" == */ ]]; do
        BASE_URL="${BASE_URL%/}"
    done

    [[ "${REQUEST_TIMEOUT}" =~ ^[1-9][0-9]*$ ]] \
        || fail "REQUEST_TIMEOUT deve ser um inteiro positivo."
    [[ "${NON_EXISTENT_ID}" =~ ^[1-9][0-9]*$ ]] \
        || fail "NON_EXISTENT_ID deve ser um inteiro positivo."

    case "${EXPECTED_CLASSIFICATION_SOURCE}" in
        ""|RULE_BASED_FALLBACK|ML_MODEL) ;;
        *) fail "EXPECTED_CLASSIFICATION_SOURCE deve ser RULE_BASED_FALLBACK, ML_MODEL ou vazia." ;;
    esac

    if [[ -n "${VALIDATED_ARTIFACT}" ]]; then
        [[ "${VALIDATED_ARTIFACT}" =~ ^[[:alnum:]._:@/+=-]{1,200}$ ]] \
            || fail "VALIDATED_ARTIFACT contém caracteres não permitidos."
        [[ "${VALIDATED_ARTIFACT,,}" != *"ocid1."* ]] \
            || fail "VALIDATED_ARTIFACT não pode conter um OCID."
    fi
}

create_temp_dir() {
    TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/backend-oci-smoke.XXXXXX")"
    chmod 700 "${TEMP_DIR}"
}

http_request() {
    local method="$1"
    local path="$2"
    local payload_file="$3"
    local expected_status="$4"
    local output_file="$5"
    local response_status
    local curl_stderr="${TEMP_DIR}/curl.stderr"
    local -a curl_args=(
        --silent
        --show-error
        --connect-timeout "${REQUEST_TIMEOUT}"
        --max-time "${REQUEST_TIMEOUT}"
        --request "${method}"
        --output "${output_file}"
        --write-out '%{http_code}'
    )

    if [[ -n "${payload_file}" ]]; then
        curl_args+=(
            --header 'Content-Type: application/json'
            --data-binary "@${payload_file}"
        )
    fi

    if ! response_status="$(curl "${curl_args[@]}" -- "${BASE_URL}${path}" 2>"${curl_stderr}")"; then
        fail "Falha de transporte ou timeout na etapa \"${CURRENT_STAGE}\"."
    fi

    if [[ "${response_status}" != "${expected_status}" ]]; then
        show_sanitized_excerpt "${output_file}"
        fail "Etapa \"${CURRENT_STAGE}\" retornou HTTP ${response_status}; esperado ${expected_status}."
    fi

    if ! jq empty "${output_file}" >/dev/null 2>&1; then
        show_sanitized_excerpt "${output_file}"
        fail "Etapa \"${CURRENT_STAGE}\" retornou JSON inválido."
    fi
}

assert_jq() {
    local response_file="$1"
    local jq_filter="$2"
    local description="$3"

    if ! jq -e "${jq_filter}" "${response_file}" >/dev/null 2>&1; then
        show_sanitized_excerpt "${response_file}"
        fail "Validação falhou na etapa \"${CURRENT_STAGE}\": ${description}."
    fi
}

assert_exact_value() {
    local response_file="$1"
    local jq_filter="$2"
    local expected_value="$3"
    local description="$4"

    if ! jq -e --arg expected "${expected_value}" \
        "(${jq_filter}) != null and ((${jq_filter}) | tostring) == \$expected" \
        "${response_file}" >/dev/null 2>&1; then
        show_sanitized_excerpt "${response_file}"
        fail "Validação falhou na etapa \"${CURRENT_STAGE}\": ${description}."
    fi
}

assert_non_null() {
    assert_jq "$1" "($2) != null" "$3"
}

assert_positive_integer() {
    assert_jq "$1" "($2) | type == \"number\" and . > 0 and floor == ." "$3"
}

assert_non_negative_integer() {
    assert_jq "$1" "($2) | type == \"number\" and . >= 0 and floor == ." "$3"
}

assert_non_negative_number() {
    assert_jq "$1" "($2) | type == \"number\" and . >= 0" "$3"
}

assert_probability() {
    assert_jq "$1" "($2) | type == \"number\" and . >= 0 and . <= 1" "$3"
}

assert_score() {
    assert_jq "$1" "($2) | type == \"number\" and . >= 0 and . <= 100 and floor == ." "$3"
}

assert_non_empty_string_array() {
    assert_jq "$1" "($2) | type == \"array\" and length > 0 and all(.[]; type == \"string\" and length > 0)" "$3"
}

assert_allowed_value() {
    local response_file="$1"
    local jq_filter="$2"
    local description="$3"
    shift 3
    local allowed_json

    allowed_json="$(printf '%s\n' "$@" | jq -R . | jq -s .)"
    if ! jq -e --argjson allowed "${allowed_json}" \
        "(${jq_filter}) as \$value | \$allowed | index(\$value) != null" \
        "${response_file}" >/dev/null 2>&1; then
        show_sanitized_excerpt "${response_file}"
        fail "Validação falhou na etapa \"${CURRENT_STAGE}\": ${description}."
    fi
}

assert_no_sensitive_content() {
    local response_file="$1"

    if LC_ALL=C grep -Eiq \
        'jdbc:|db_password|password|passwd|authorization|spring[.]datasource|oracle[.]jdbc|stack[ _-]?trace|stacktrace|ocid1[.]' \
        "${response_file}"; then
        fail "A etapa \"${CURRENT_STAGE}\" expôs conteúdo interno ou sensível."
    fi
}

run_health_scenario() {
    local stage="$1"
    local path="$2"
    local output_file="$3"

    CURRENT_STAGE="${stage}"
    info "Validando ${stage}."
    http_request GET "${path}" "" 200 "${output_file}"
    assert_exact_value "${output_file}" '.status' UP 'status deve ser UP'
    assert_no_sensitive_content "${output_file}"
    pass "${stage} respondeu UP."
}

capture_initial_total() {
    local response_file="${TEMP_DIR}/summary-initial.json"

    CURRENT_STAGE="total inicial persistido"
    info "Capturando total inicial pela API pública."
    http_request GET '/analise-energetica/resumo' "" 200 "${response_file}"
    assert_non_negative_integer "${response_file}" '.total_analises' \
        'total_analises deve ser um inteiro não negativo'
    assert_no_sensitive_content "${response_file}"
    INITIAL_TOTAL="$(jq -r '.total_analises' "${response_file}")"
    pass "Total inicial capturado."
}

create_valid_analysis() {
    local response_file="${TEMP_DIR}/create-valid.json"

    CURRENT_STAGE="criação de análise válida"
    info "Criando análise energética válida."
    http_request POST '/analise-energetica' "${VALID_PAYLOAD}" 200 "${response_file}"
    assert_positive_integer "${response_file}" '.id' 'id deve ser um inteiro positivo'
    assert_allowed_value "${response_file}" '.categoria' 'categoria deve ser suportada' \
        EFICIENTE MODERADO INEFICIENTE
    assert_probability "${response_file}" '.probabilidade' 'probabilidade deve estar entre 0 e 1'
    assert_score "${response_file}" '.score' 'score deve ser inteiro entre 0 e 100'
    assert_non_negative_number "${response_file}" '.custo_estimado_mensal' \
        'custo_estimado_mensal deve ser um número não negativo'
    assert_non_empty_string_array "${response_file}" '.recomendacoes' \
        'recomendacoes deve ser um array não vazio de strings não vazias'
    assert_allowed_value "${response_file}" '.fonte_classificacao' \
        'fonte_classificacao deve ser suportada' RULE_BASED ML_MODEL RULE_BASED_FALLBACK
    assert_no_sensitive_content "${response_file}"

    if [[ -n "${EXPECTED_CLASSIFICATION_SOURCE}" ]]; then
        assert_exact_value "${response_file}" '.fonte_classificacao' \
            "${EXPECTED_CLASSIFICATION_SOURCE}" \
            'fonte_classificacao deve corresponder ao modo esperado'
    fi

    CREATED_ID="$(jq -r '.id' "${response_file}")"
    CREATED_CATEGORY="$(jq -r '.categoria' "${response_file}")"
    CREATED_PROBABILITY="$(jq -r '.probabilidade' "${response_file}")"
    CREATED_SCORE="$(jq -r '.score' "${response_file}")"
    CREATED_COST="$(jq -r '.custo_estimado_mensal' "${response_file}")"
    CREATED_SOURCE="$(jq -r '.fonte_classificacao' "${response_file}")"
    pass "Análise válida criada com ID ${CREATED_ID} e fonte ${CREATED_SOURCE}."
}

confirm_history_persistence() {
    local response_file="${TEMP_DIR}/history.json"
    local created_id="${CREATED_ID}"

    CURRENT_STAGE="persistência no histórico"
    info "Consultando histórico pela API pública."
    http_request GET '/analise-energetica?page=0&size=100&sort=createdAt,desc' "" 200 "${response_file}"
    assert_jq "${response_file}" '.analises | type == "array"' 'analises deve ser um array'
    assert_non_negative_integer "${response_file}" '.pagina_atual' \
        'pagina_atual deve ser um inteiro não negativo'
    assert_positive_integer "${response_file}" '.tamanho_pagina' \
        'tamanho_pagina deve ser um inteiro positivo'
    assert_non_negative_integer "${response_file}" '.total_elementos' \
        'total_elementos deve ser um inteiro não negativo'
    assert_non_negative_integer "${response_file}" '.total_paginas' \
        'total_paginas deve ser um inteiro não negativo'
    assert_jq "${response_file}" \
        "any(.analises[]; .id == ${created_id})" \
        'histórico deve conter exatamente o ID criado'
    assert_jq "${response_file}" \
        "first(.analises[] | select(.id == ${created_id})) \
            | (.categoria != null \
                and .probabilidade != null \
                and .score != null \
                and .custo_estimado_mensal != null \
                and (.criado_em | type == \"string\" and length > 0) \
                and (has(\"custoEstimadoMensal\") | not) \
                and (has(\"criadoEm\") | not))" \
        'resumo criado deve expor campos calculados em snake_case'
    assert_jq "${response_file}" \
        '([.analises[].criado_em] == ([.analises[].criado_em] | sort | reverse))' \
        'histórico deve respeitar a ordenação decrescente solicitada'
    assert_no_sensitive_content "${response_file}"
    pass "Análise criada localizada no histórico pelo ID retornado."
}

confirm_detail_persistence() {
    local response_file="${TEMP_DIR}/detail.json"

    CURRENT_STAGE="persistência no detalhe"
    info "Consultando detalhe pelo ID retornado."
    http_request GET "/analise-energetica/${CREATED_ID}" "" 200 "${response_file}"
    assert_exact_value "${response_file}" '.id' "${CREATED_ID}" 'id deve corresponder ao registro criado'
    if ! jq -e --slurpfile expected "${VALID_PAYLOAD}" \
        '.consumo_kwh == $expected[0].consumo_kwh
            and .uso_horario_pico == $expected[0].uso_horario_pico
            and .quantidade_equipamentos == $expected[0].quantidade_equipamentos
            and .tipo_imovel == $expected[0].tipo_imovel
            and .horas_alto_consumo == $expected[0].horas_alto_consumo' \
        "${response_file}" >/dev/null 2>&1; then
        show_sanitized_excerpt "${response_file}"
        fail "Validação falhou na etapa \"${CURRENT_STAGE}\": entradas persistidas divergem do payload."
    fi
    if ! jq -e \
        --arg category "${CREATED_CATEGORY}" \
        --argjson probability "${CREATED_PROBABILITY}" \
        --argjson score "${CREATED_SCORE}" \
        --argjson cost "${CREATED_COST}" \
        --arg source "${CREATED_SOURCE}" \
        '.categoria == $category
            and .probabilidade == $probability
            and .score == $score
            and .custo_estimado_mensal == $cost
            and .fonte_classificacao == $source' \
        "${response_file}" >/dev/null 2>&1; then
        show_sanitized_excerpt "${response_file}"
        fail "Validação falhou na etapa \"${CURRENT_STAGE}\": campos calculados divergem da criação."
    fi
    assert_non_empty_string_array "${response_file}" '.recomendacoes' \
        'recomendacoes persistidas devem ser não vazias'
    assert_jq "${response_file}" '.criado_em | type == "string" and length > 0' \
        'criado_em deve ser uma string não vazia'
    assert_jq "${response_file}" \
        '((has("createdAt") or has("updatedAt") or has("version")
            or has("hibernateLazyInitializer") or has("handler")) | not)' \
        'resposta não deve expor campos internos da entidade'
    assert_no_sensitive_content "${response_file}"
    pass "Detalhe persistido corresponde à entrada e ao resultado criado."
}

confirm_total_after_valid() {
    local response_file="${TEMP_DIR}/summary-after-valid.json"
    local expected_total=$((INITIAL_TOTAL + 1))

    CURRENT_STAGE="total após análise válida"
    info "Confirmando incremento do total persistido."
    http_request GET '/analise-energetica/resumo' "" 200 "${response_file}"
    assert_non_negative_integer "${response_file}" '.total_analises' \
        'total_analises deve ser um inteiro não negativo'
    assert_exact_value "${response_file}" '.total_analises' "${expected_total}" \
        'total deve aumentar exatamente em uma análise'
    assert_no_sensitive_content "${response_file}"
    TOTAL_AFTER_VALID="$(jq -r '.total_analises' "${response_file}")"
    pass "Total incrementado exatamente em uma análise."
}

reject_invalid_analysis() {
    local response_file="${TEMP_DIR}/create-invalid.json"

    CURRENT_STAGE="rejeição de entrada inválida"
    info "Enviando violações determinísticas de Bean Validation."
    http_request POST '/analise-energetica' "${INVALID_PAYLOAD}" 400 "${response_file}"
    assert_exact_value "${response_file}" '.status' 400 'status público deve ser 400'
    assert_exact_value "${response_file}" '.error' VALIDATION_ERROR \
        'error deve ser VALIDATION_ERROR'
    assert_jq "${response_file}" '.timestamp | type == "string" and length > 0' \
        'timestamp deve ser uma string não vazia'
    assert_jq "${response_file}" '.message | type == "string" and length > 0' \
        'message deve ser uma string não vazia'
    assert_jq "${response_file}" \
        '.message | contains("consumo_kwh")
            and contains("quantidade_equipamentos")
            and contains("horas_alto_consumo")' \
        'message deve identificar os campos públicos inválidos'
    assert_no_sensitive_content "${response_file}"
    pass "Entrada inválida rejeitada pelo contrato VALIDATION_ERROR."
}

confirm_invalid_not_persisted() {
    local response_file="${TEMP_DIR}/summary-after-invalid.json"

    CURRENT_STAGE="não persistência da entrada inválida"
    info "Confirmando que a entrada inválida não alterou o total."
    http_request GET '/analise-energetica/resumo' "" 200 "${response_file}"
    assert_non_negative_integer "${response_file}" '.total_analises' \
        'total_analises deve ser um inteiro não negativo'
    assert_exact_value "${response_file}" '.total_analises' "${TOTAL_AFTER_VALID}" \
        'entrada inválida não pode alterar o total persistido'
    assert_no_sensitive_content "${response_file}"
    pass "Entrada inválida não foi persistida."
}

confirm_nonexistent_resource() {
    local response_file="${TEMP_DIR}/not-found.json"

    CURRENT_STAGE="recurso inexistente"
    info "Consultando um ID inexistente."
    http_request GET "/analise-energetica/${NON_EXISTENT_ID}" "" 404 "${response_file}"
    assert_exact_value "${response_file}" '.status' 404 'status público deve ser 404'
    assert_exact_value "${response_file}" '.error' NOT_FOUND_ERROR \
        'error deve ser NOT_FOUND_ERROR'
    assert_jq "${response_file}" '.timestamp | type == "string" and length > 0' \
        'timestamp deve ser uma string não vazia'
    assert_jq "${response_file}" '.message | type == "string" and length > 0' \
        'message deve ser uma string não vazia'
    assert_no_sensitive_content "${response_file}"
    pass "Recurso inexistente preserva o contrato NOT_FOUND_ERROR."
}

confirm_classification_postcondition() {
    if [[ "${EXPECTED_CLASSIFICATION_SOURCE}" == RULE_BASED_FALLBACK ]]; then
        run_health_scenario \
            'readiness após fallback' \
            '/actuator/health/readiness' \
            "${TEMP_DIR}/readiness-after-fallback.json"
        pass "Fallback confirmado sem perda de readiness."
    elif [[ "${EXPECTED_CLASSIFICATION_SOURCE}" == ML_MODEL ]]; then
        pass "Classificação ML_MODEL confirmada pelo contrato público."
    else
        pass "Fonte de classificação pública válida confirmada (${CREATED_SOURCE})."
    fi
}

main() {
    CURRENT_STAGE="validação de pré-requisitos"
    validate_configuration
    check_dependencies
    create_temp_dir

    info "Início UTC: $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    if [[ -n "${VALIDATED_ARTIFACT}" ]]; then
        info "Artefato validado: ${VALIDATED_ARTIFACT}"
    fi
    pass "Configuração, dependências, fixtures e diretório temporário validados."

    run_health_scenario 'health geral' '/actuator/health' "${TEMP_DIR}/health.json"
    run_health_scenario 'liveness' '/actuator/health/liveness' "${TEMP_DIR}/liveness.json"
    run_health_scenario 'readiness' '/actuator/health/readiness' "${TEMP_DIR}/readiness.json"
    capture_initial_total
    create_valid_analysis
    confirm_history_persistence
    confirm_detail_persistence
    confirm_total_after_valid
    reject_invalid_analysis
    confirm_invalid_not_persisted
    confirm_nonexistent_resource
    confirm_classification_postcondition

    CURRENT_STAGE="conclusão"
    pass "Todos os cenários obrigatórios foram concluídos."
}

main "$@"
