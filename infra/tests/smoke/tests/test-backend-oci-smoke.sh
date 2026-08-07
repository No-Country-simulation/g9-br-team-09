#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly SMOKE_SCRIPT="${SCRIPT_DIR}/../backend-oci-smoke.sh"
readonly TEST_EMAIL='smoke.user@example.invalid'
readonly TEST_PASSWORD='test-only-smoke-password'

fixture_dir=""
test_token=""

cleanup() {
    if [[ -n "${fixture_dir}" && -d "${fixture_dir}" ]]; then
        rm -rf -- "${fixture_dir}"
    fi
}

trap cleanup EXIT

assert_contains() {
    local output="$1"
    local expected="$2"

    [[ "${output}" == *"${expected}"* ]] \
        || { printf 'Expected output to contain: %s\n%s\n' "${expected}" "${output}" >&2; return 1; }
}

assert_not_contains() {
    local output="$1"
    local unexpected="$2"

    [[ "${output}" != *"${unexpected}"* ]] \
        || { printf 'Expected output not to contain: %s\n%s\n' "${unexpected}" "${output}" >&2; return 1; }
}

assert_before() {
    local output="$1"
    local first="$2"
    local second="$3"

    [[ "${output}" == *"${first}"*"${second}"* ]] \
        || { printf 'Expected output in order: %s before %s\n%s\n' "${first}" "${second}" "${output}" >&2; return 1; }
}

create_fixture() {
    fixture_dir="$(mktemp -d)"
    test_token="issued-token.$(basename -- "${fixture_dir}")"
    mkdir -p "${fixture_dir}/bin" "${fixture_dir}/tmp"
    printf '%s\0%s\0' "${TEST_EMAIL}" "${TEST_PASSWORD}" >"${fixture_dir}/smoke-auth.credentials"
    chmod 600 "${fixture_dir}/smoke-auth.credentials"

    cat >"${fixture_dir}/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

method=GET
output_file=""
payload_file=""
url=""
curl_config=""

while (($# > 0)); do
    case "$1" in
        --config)
            [[ "$2" == - ]]
            curl_config="$(cat)"
            shift 2
            ;;
        --request)
            method="$2"
            shift 2
            ;;
        --output)
            output_file="$2"
            shift 2
            ;;
        --data-binary)
            payload_file="${2#@}"
            shift 2
            ;;
        --header|--connect-timeout|--max-time|--write-out)
            shift 2
            ;;
        --silent|--show-error)
            shift
            ;;
        --)
            url="$2"
            shift 2
            ;;
        *)
            printf 'Unexpected curl argument: %s\n' "$1" >&2
            exit 90
            ;;
    esac
done

path="${url#*'/api/v1'}"
authenticated=false
if [[ -n "${curl_config}" ]]; then
    [[ "${curl_config}" == "header = \"Authorization: Bearer ${EXPECTED_FAKE_TOKEN}\"" ]] || exit 88
    authenticated=true
fi

case "${path}" in
    /actuator/*|/auth/login)
        [[ "${authenticated}" == false ]] || exit 87
        ;;
    /auth/me|/analise-energetica*)
        [[ "${authenticated}" == true ]] || exit 86
        ;;
    *) exit 85 ;;
esac

printf '%s %s auth=%s\n' "${method}" "${path}" "${authenticated}" >>"${FAKE_CURL_CALLS}"

case "${method} ${path}" in
    'GET /actuator/health'|'GET /actuator/health/liveness'|'GET /actuator/health/readiness')
        printf '%s\n' '{"status":"UP"}' >"${output_file}"
        status=200
        ;;
    'POST /auth/login')
        if [[ "${FAKE_LOGIN_STATUS:-200}" == 401 ]]; then
            printf '%s\n' '{"status":401,"error":"UNAUTHORIZED_ERROR","message":"Credenciais inválidas"}' >"${output_file}"
            status=401
        else
            jq -e \
                '.email == env.EXPECTED_FAKE_EMAIL and .senha == env.EXPECTED_FAKE_PASSWORD' \
                "${payload_file}" >/dev/null
            printf '{"access_token":"%s","token_type":"Bearer","expires_in":900}\n' \
                "${EXPECTED_FAKE_TOKEN}" >"${output_file}"
            status=200
        fi
        ;;
    'GET /auth/me')
        printf '{"id":42,"nome":"Smoke OCI","email":"%s","role":"USER","criado_em":"2026-08-06T12:00:00"}\n' \
            "${FAKE_ME_EMAIL:-${EXPECTED_FAKE_EMAIL}}" >"${output_file}"
        status=200
        ;;
    'GET /analise-energetica/resumo')
        if [[ -f "${FAKE_CREATED_STATE}" ]]; then
            printf '%s\n' '{"total_analises":8}' >"${output_file}"
        else
            printf '%s\n' '{"total_analises":7}' >"${output_file}"
        fi
        status=200
        ;;
    'POST /analise-energetica')
        if [[ "${payload_file}" == *payload-invalid.json ]]; then
            printf '%s\n' '{"timestamp":"2026-08-06T12:00:00Z","status":400,"error":"VALIDATION_ERROR","message":"consumo_kwh quantidade_equipamentos horas_alto_consumo"}' >"${output_file}"
            status=400
        else
            : >"${FAKE_CREATED_STATE}"
            printf '%s\n' '{"id":101,"categoria":"EFICIENTE","probabilidade":0.91,"score":91,"custo_estimado_mensal":123.45,"recomendacoes":["Teste"],"fonte_classificacao":"RULE_BASED_FALLBACK"}' >"${output_file}"
            status=200
        fi
        ;;
    'GET /analise-energetica?page=0&size=100&sort=createdAt,desc')
        printf '%s\n' '{"analises":[{"id":101,"categoria":"EFICIENTE","probabilidade":0.91,"score":91,"custo_estimado_mensal":123.45,"criado_em":"2026-08-06T12:00:00Z"}],"pagina_atual":0,"tamanho_pagina":100,"total_elementos":1,"total_paginas":1}' >"${output_file}"
        status=200
        ;;
    'GET /analise-energetica/101')
        printf '%s\n' '{"id":101,"consumo_kwh":420,"uso_horario_pico":true,"quantidade_equipamentos":10,"tipo_imovel":"CASA","horas_alto_consumo":8,"categoria":"EFICIENTE","probabilidade":0.91,"score":91,"custo_estimado_mensal":123.45,"recomendacoes":["Teste"],"fonte_classificacao":"RULE_BASED_FALLBACK","criado_em":"2026-08-06T12:00:00Z"}' >"${output_file}"
        status=200
        ;;
    'GET /analise-energetica/9223372036854775807')
        printf '%s\n' '{"timestamp":"2026-08-06T12:00:00Z","status":404,"error":"NOT_FOUND_ERROR","message":"Análise não encontrada"}' >"${output_file}"
        status=404
        ;;
    *) exit 84 ;;
esac

printf '%s' "${status}"
EOF
    chmod 700 "${fixture_dir}/bin/curl"
}

run_smoke() {
    PATH="${fixture_dir}/bin:${PATH}" \
    TMPDIR="${fixture_dir}/tmp" \
    BASE_URL='http://127.0.0.1:8080/api/v1' \
    SMOKE_AUTH_FILE="${fixture_dir}/smoke-auth.credentials" \
    EXPECTED_CLASSIFICATION_SOURCE=RULE_BASED_FALLBACK \
    EXPECTED_FAKE_EMAIL="${TEST_EMAIL}" \
    EXPECTED_FAKE_PASSWORD="${TEST_PASSWORD}" \
    EXPECTED_FAKE_TOKEN="${test_token}" \
    FAKE_ME_EMAIL="${FAKE_ME_EMAIL:-${TEST_EMAIL}}" \
    FAKE_CURL_CALLS="${fixture_dir}/curl.calls" \
    FAKE_CREATED_STATE="${fixture_dir}/created.state" \
        bash "${SMOKE_SCRIPT}" 2>&1
}

test_authenticated_flow_and_secret_redaction() {
    local calls
    local output

    create_fixture
    output="$(run_smoke)"
    calls="$(<"${fixture_dir}/curl.calls")"

    assert_contains "${output}" 'Todos os cenários obrigatórios foram concluídos.'
    assert_before "${calls}" 'POST /auth/login auth=false' 'GET /auth/me auth=true'
    assert_before "${calls}" 'GET /auth/me auth=true' 'GET /analise-energetica/resumo auth=true'
    assert_contains "${calls}" 'GET /actuator/health auth=false'
    assert_contains "${calls}" 'GET /actuator/health/liveness auth=false'
    assert_contains "${calls}" 'GET /actuator/health/readiness auth=false'
    assert_contains "${calls}" 'POST /analise-energetica auth=true'
    assert_contains "${calls}" 'GET /analise-energetica/101 auth=true'
    assert_contains "${calls}" 'GET /analise-energetica/9223372036854775807 auth=true'
    assert_not_contains "${output}" "${TEST_EMAIL}"
    assert_not_contains "${output}" "${TEST_PASSWORD}"
    assert_not_contains "${output}" "${test_token}"
    assert_not_contains "${output}" 'Authorization'
    assert_not_contains "${output}" 'Bearer'
    [[ -z "$(find "${fixture_dir}/tmp" -mindepth 1 -maxdepth 1 -print -quit)" ]]
    cleanup
    fixture_dir=""
}

test_missing_credential_file_fails_before_http() {
    local output
    local status

    create_fixture
    set +e
    output="$(
        PATH="${fixture_dir}/bin:${PATH}" \
        BASE_URL='http://127.0.0.1:8080/api/v1' \
        SMOKE_AUTH_FILE="${fixture_dir}/missing.credentials" \
        FAKE_CURL_CALLS="${fixture_dir}/curl.calls" \
            bash "${SMOKE_SCRIPT}" 2>&1
    )"
    status=$?
    set -e
    [[ "${status}" -ne 0 ]]
    assert_contains "${output}" 'arquivo temporário de credenciais do smoke test é obrigatório'
    [[ ! -e "${fixture_dir}/curl.calls" ]]
    cleanup
    fixture_dir=""
}

test_permissive_credentials_fail_before_http() {
    local output
    local status

    create_fixture
    chmod 0644 "${fixture_dir}/smoke-auth.credentials"
    set +e
    output="$(run_smoke)"
    status=$?
    set -e
    [[ "${status}" -ne 0 ]]
    assert_contains "${output}" 'credenciais do smoke test não possuem permissões restritivas'
    [[ ! -e "${fixture_dir}/curl.calls" ]]
    assert_not_contains "${output}" "${TEST_EMAIL}"
    assert_not_contains "${output}" "${TEST_PASSWORD}"
    cleanup
    fixture_dir=""
}

test_incomplete_credentials_fail_before_http() {
    local output
    local status

    create_fixture
    printf '%s\0' "${TEST_EMAIL}" >"${fixture_dir}/smoke-auth.credentials"
    chmod 600 "${fixture_dir}/smoke-auth.credentials"
    set +e
    output="$(
        PATH="${fixture_dir}/bin:${PATH}" \
        BASE_URL='http://127.0.0.1:8080/api/v1' \
        SMOKE_AUTH_FILE="${fixture_dir}/smoke-auth.credentials" \
        FAKE_CURL_CALLS="${fixture_dir}/curl.calls" \
            bash "${SMOKE_SCRIPT}" 2>&1
    )"
    status=$?
    set -e
    [[ "${status}" -ne 0 ]]
    assert_contains "${output}" 'credenciais do smoke test estão incompletas'
    assert_not_contains "${output}" "${TEST_EMAIL}"
    [[ ! -e "${fixture_dir}/curl.calls" ]]
    cleanup
    fixture_dir=""
}

test_invalid_login_stops_before_protected_requests() {
    local calls
    local output
    local status

    create_fixture
    set +e
    output="$(FAKE_LOGIN_STATUS=401 run_smoke)"
    status=$?
    set -e
    [[ "${status}" -ne 0 ]]
    calls="$(<"${fixture_dir}/curl.calls")"
    assert_contains "${output}" 'retornou HTTP 401; esperado 200'
    assert_contains "${calls}" 'POST /auth/login auth=false'
    assert_not_contains "${calls}" '/auth/me'
    assert_not_contains "${calls}" '/analise-energetica'
    assert_not_contains "${output}" "${TEST_EMAIL}"
    assert_not_contains "${output}" "${TEST_PASSWORD}"
    assert_not_contains "${output}" "${test_token}"
    cleanup
    fixture_dir=""
}

test_mismatched_authenticated_identity_fails() {
    local calls
    local mismatched_email='different.smoke.user@example.invalid'
    local output
    local status

    create_fixture
    set +e
    output="$(FAKE_ME_EMAIL="${mismatched_email}" run_smoke)"
    status=$?
    set -e
    [[ "${status}" -ne 0 ]]
    calls="$(<"${fixture_dir}/curl.calls")"
    assert_contains "${output}" 'email deve corresponder ao usuário técnico dedicado'
    assert_contains "${calls}" 'GET /auth/me auth=true'
    assert_not_contains "${calls}" '/analise-energetica'
    assert_not_contains "${output}" "${TEST_EMAIL}"
    assert_not_contains "${output}" "${mismatched_email}"
    cleanup
    fixture_dir=""
}

test_authenticated_flow_and_secret_redaction
test_missing_credential_file_fails_before_http
test_permissive_credentials_fail_before_http
test_incomplete_credentials_fail_before_http
test_invalid_login_stops_before_protected_requests
test_mismatched_authenticated_identity_fails
printf 'PASS test-backend-oci-smoke.sh\n'
