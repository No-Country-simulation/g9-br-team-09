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

    excerpt="$(head -c 512 -- "${response_file}" \
        | tr '\r\n\t' ' ' \
        | sed -E \
            -e "s#(jdbc:)[^,\"[:space:]]+#\1[REDACTED]#Ig" \
            -e "s#((password|passwd|secret|token|authorization|db_password)[[:space:]]*[:=][[:space:]]*)[^,[:space:]]+#\1[REDACTED]#Ig" \
            -e "s#(ocid1\.)[[:alnum:]_.-]+#\1[REDACTED]#Ig")"

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
    [[ "${BASE_URL}" =~ ^https?:// ]] || fail "BASE_URL deve usar HTTP ou HTTPS."
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

    [[ ! "${VALIDATED_ARTIFACT}" =~ [[:cntrl:]] ]] \
        || fail "VALIDATED_ARTIFACT contém caracteres não permitidos."
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
        'jdbc:|db_password|spring[.]datasource|oracle[.]jdbc|stack[ _-]?trace|stacktrace|ocid1[.]' \
        "${response_file}"; then
        fail "A etapa \"${CURRENT_STAGE}\" expôs conteúdo interno ou sensível."
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
}

main "$@"
