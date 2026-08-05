#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly DEPLOY_HELPER="${SCRIPT_DIR}/../deploy-backend.sh"

if [[ "${SMOKE_PROBE_MODE:-}" == 1 ]]; then
    [[ "${EXPECTED_CLASSIFICATION_SOURCE:-}" == ML_MODEL ]]
    [[ "${BASE_URL:-}" == http://127.0.0.1:8080/api/v1 ]]
    [[ "${REQUEST_TIMEOUT:-}" == 15 ]]
    [[ "${VALIDATED_ARTIFACT:-}" == \
        docker.io/pxs00/energiai-backend:sha-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc ]]
    printf '[TEST] smoke environment received\n'
    exit "${SMOKE_STATUS:-0}"
fi

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

run_failure_case() {
    local command="$1"
    local output
    local status

    set +e
    output="$(bash -c "${command}" bash "${DEPLOY_HELPER}" 2>&1)"
    status=$?
    set -e
    [[ "${status}" -eq 1 ]] \
        || { printf 'Expected exit status 1, got %s.\n%s\n' "${status}" "${output}" >&2; return 1; }
    printf '%s' "${output}"
}

run_smoke_main_case() {
    local smoke_status="$1"
    local expected_status="$2"
    local fixture_dir
    local output
    local status

    fixture_dir="$(mktemp -d)"
    mkdir -p \
        "${fixture_dir}/.git" \
        "${fixture_dir}/infra/deploy/oci" \
        "${fixture_dir}/infra/tests/smoke"
    touch \
        "${fixture_dir}/backend.env" \
        "${fixture_dir}/infra/deploy/oci/compose.yaml"
    cp -- \
        "${SCRIPT_DIR}/test-deploy-backend.sh" \
        "${fixture_dir}/infra/tests/smoke/backend-oci-smoke.sh"
    chmod 700 -- "${fixture_dir}/infra/tests/smoke/backend-oci-smoke.sh"

    set +e
    output="$(
        REPOSITORY_DIR="${fixture_dir}" \
        BACKEND_ENV_FILE="${fixture_dir}/backend.env" \
        TARGET_COMMIT='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
        TARGET_IMAGE='docker.io/pxs00/energiai-backend:sha-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
        IMAGE_DIGEST='sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc' \
        EXPECTED_CLASSIFICATION_SOURCE=ML_MODEL \
        SMOKE_PROBE_MODE=1 \
        SMOKE_STATUS="${smoke_status}" \
        bash -c '
            source "$1"
            cleanup() { :; }
            require_dependencies() { :; }
            require_restricted_file() { :; }
            configure_temporary_docker_config() { :; }
            compose() { :; }
            login_to_dockerhub() { :; }
            checkout_commit() { :; }
            replace_backend_image() { :; }
            pull_and_verify_target_image() { :; }
            wait_for_readiness() { readiness_result="passed"; }
            read_backend_image() {
                printf "%s\\n" "docker.io/pxs00/energiai-backend:sha-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
            git() {
                if [[ "$*" == *"status --porcelain"* ]]; then
                    return 0
                fi
                if [[ "$*" == *"rev-parse --verify"* ]]; then
                    printf "%s\\n" "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    return 0
                fi
                return 1
            }
            docker() {
                if [[ "${1:-}" == "inspect" ]]; then
                    printf "%s\\n" "docker.io/pxs00/energiai-backend:sha-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                fi
            }
            capture_failed_deployment_diagnostics() {
                printf "[TEST] diagnostics capture invoked\\n"
            }
            rollback() {
                printf "[TEST] rollback invoked\\n"
                return 0
            }
            main
        ' bash "${DEPLOY_HELPER}" 2>&1
    )"
    status=$?
    set -e
    rm -rf -- "${fixture_dir}"

    [[ "${status}" -eq "${expected_status}" ]] \
        || { printf 'Expected exit status %s, got %s.\n%s\n' "${expected_status}" "${status}" "${output}" >&2; return 1; }
    printf '%s' "${output}"
}

test_fail_triggers_on_error() {
    local output

    output="$(run_failure_case '
        source "$1"
        require_immutable_image() {
            fail "preflight failure"
        }
        main
    ')"
    assert_contains "${output}" '[FAIL] preflight failure'
    assert_contains "${output}" 'DEPLOY_RESULT=failed-before-update'
    assert_contains "${output}" 'ROLLBACK_ATTEMPTED=no'
}

test_deployment_failure_attempts_rollback() {
    local output

    output="$(run_failure_case '
        source "$1"
        trap cleanup EXIT
        trap on_error ERR
        capture_failed_deployment_diagnostics() {
            printf "[TEST] diagnostics capture invoked\\n"
        }
        rollback() {
            printf "[TEST] rollback invoked\\n"
            return 0
        }
        deployment_changed=true
        false
    ')"
    assert_before "${output}" '[TEST] diagnostics capture invoked' '[TEST] rollback invoked'
    assert_contains "${output}" '[TEST] rollback invoked'
    assert_contains "${output}" 'DEPLOY_RESULT=failed-rolled-back'
    assert_contains "${output}" 'ROLLBACK_ATTEMPTED=yes'
    assert_contains "${output}" 'ROLLBACK_RESULT=succeeded'
}

test_pre_update_failure_emits_structured_result() {
    local output

    output="$(run_failure_case '
        source "$1"
        trap cleanup EXIT
        trap on_error ERR
        capture_failed_deployment_diagnostics() {
            printf "[TEST] diagnostics capture invoked\\n"
        }
        false
    ')"
    assert_contains "${output}" 'DEPLOY_RESULT=failed-before-update'
    assert_contains "${output}" 'ROLLBACK_ATTEMPTED=no'
    assert_contains "${output}" 'ROLLBACK_RESULT=not-required'
    assert_not_contains "${output}" '[TEST] diagnostics capture invoked'
}

test_successful_smoke_does_not_roll_back() {
    local output

    output="$(run_smoke_main_case 0 0)"
    assert_contains "${output}" '[TEST] smoke environment received'
    assert_contains "${output}" 'DEPLOY_RESULT=success'
    assert_contains "${output}" 'SMOKE_TEST_RESULT=passed'
    assert_contains "${output}" 'ROLLBACK_ATTEMPTED=no'
    assert_not_contains "${output}" '[TEST] diagnostics capture invoked'
    assert_not_contains "${output}" '[TEST] rollback invoked'
}

test_failed_smoke_rolls_back_and_preserves_status() {
    local output

    output="$(run_smoke_main_case 1 1)"
    assert_contains "${output}" '[TEST] smoke environment received'
    assert_before "${output}" '[TEST] diagnostics capture invoked' '[TEST] rollback invoked'
    assert_contains "${output}" '[TEST] rollback invoked'
    assert_contains "${output}" 'DEPLOY_RESULT=failed-rolled-back'
    assert_contains "${output}" 'SMOKE_TEST_RESULT=failed'
    assert_contains "${output}" 'ROLLBACK_ATTEMPTED=yes'
    assert_contains "${output}" 'ROLLBACK_RESULT=succeeded'
    assert_not_contains "${output}" 'readonly variable'
}

test_capture_failure_does_not_prevent_rollback() {
    local output

    output="$(run_failure_case '
        source "$1"
        trap cleanup EXIT
        trap on_error ERR
        capture_failed_deployment_diagnostics() {
            printf "[TEST] diagnostics capture failed\\n"
            return 1
        }
        rollback() {
            printf "[TEST] rollback invoked\\n"
            return 0
        }
        deployment_changed=true
        false
    ')"
    assert_before "${output}" '[TEST] diagnostics capture failed' '[TEST] rollback invoked'
    assert_contains "${output}" 'AVISO: não foi possível preservar o diagnóstico do candidato; o rollback continuará.'
    assert_contains "${output}" 'DEPLOY_RESULT=failed-rolled-back'
    assert_contains "${output}" 'ROLLBACK_RESULT=succeeded'
}

test_non_candidate_container_creates_no_diagnostic() {
    local fixture_dir

    fixture_dir="$(mktemp -d)"
    DEPLOY_DIAGNOSTICS_DIR="${fixture_dir}/diagnostics" \
    TARGET_COMMIT='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
    TARGET_IMAGE='docker.io/pxs00/energiai-backend:sha-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
        bash -c '
            source "$1"
            timeout() {
                shift 3
                "$@"
            }
            docker() {
                printf "%s\\n" \
                    "container_image=docker.io/pxs00/energiai-backend:sha-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
                    "container_status=running" \
                    "exit_code=0" \
                    "oom_killed=false" \
                    "restart_count=0" \
                    "docker_error=\"\""
            }
            capture_failed_deployment_diagnostics
            [[ ! -e "${DEPLOY_DIAGNOSTICS_DIR}" ]]
        ' bash "${DEPLOY_HELPER}"
    rm -rf -- "${fixture_dir}"
}

test_log_capture_failure_preserves_metadata_and_emits_warning() {
    local fixture_dir
    local output

    fixture_dir="$(mktemp -d)"
    output="$(
        DEPLOY_DIAGNOSTICS_DIR="${fixture_dir}/diagnostics" \
        TARGET_COMMIT='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
        TARGET_IMAGE='docker.io/pxs00/energiai-backend:sha-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
            bash -c '
                source "$1"
                timeout() {
                    shift 3
                    "$@"
                }
                docker() {
                    if [[ "${1:-}" == inspect ]]; then
                        printf "%s\\n" \
                            "container_image=${TARGET_IMAGE}" \
                            "container_status=exited" \
                            "exit_code=1" \
                            "oom_killed=false" \
                            "restart_count=0" \
                            "docker_error=\\"\\""
                        return 0
                    fi
                    printf "%s\\n" "Error response from daemon" >&2
                    return 1
                }
                capture_failed_deployment_diagnostics
            ' bash "${DEPLOY_HELPER}" 2>&1
    )"
    assert_contains "${output}" 'AVISO: os logs recentes do candidato não puderam ser coletados'
    grep -Fqx 'recent_logs_unavailable=true' \
        "${fixture_dir}/diagnostics/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.log"
    grep -Fqx 'container_status=exited' \
        "${fixture_dir}/diagnostics/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.log"
    rm -rf -- "${fixture_dir}"
}

test_inspect_timeout_does_not_prevent_rollback_or_replace_failure_status() {
    local fixture_dir
    local output
    local status

    fixture_dir="$(mktemp -d)"
    set +e
    output="$(
        DEPLOY_DIAGNOSTICS_DIR="${fixture_dir}/diagnostics" \
        TARGET_COMMIT='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
        TARGET_IMAGE='docker.io/pxs00/energiai-backend:sha-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
            bash -c '
                source "$1"
                run_diagnostic_command_with_timeout() {
                    return 124
                }
                rollback() {
                    printf "[TEST] rollback invoked after inspect timeout\\n"
                    return 0
                }
                deployment_changed=true
                handle_failure 7
            ' bash "${DEPLOY_HELPER}" 2>&1
    )"
    status=$?
    set -e
    [[ "${status}" -eq 7 ]] \
        || { printf 'Expected original status 7, got %s.\n%s\n' "${status}" "${output}" >&2; return 1; }
    assert_contains "${output}" 'AVISO: não foi possível preservar o diagnóstico do candidato'
    assert_contains "${output}" '[TEST] rollback invoked after inspect timeout'
    assert_contains "${output}" 'DEPLOY_RESULT=failed-rolled-back'
    [[ ! -e "${fixture_dir}/diagnostics" ]]
    rm -rf -- "${fixture_dir}"
}

test_log_timeout_preserves_metadata_and_does_not_prevent_rollback() {
    local fixture_dir
    local output
    local status

    fixture_dir="$(mktemp -d)"
    set +e
    output="$(
        DEPLOY_DIAGNOSTICS_DIR="${fixture_dir}/diagnostics" \
        TARGET_COMMIT='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
        TARGET_IMAGE='docker.io/pxs00/energiai-backend:sha-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
            bash -c '
                source "$1"
                run_diagnostic_command_with_timeout() {
                    if [[ "$*" == *"docker logs"* ]]; then
                        return 124
                    fi
                    shift
                    "$@"
                }
                docker() {
                    printf "%s\\n" \
                        "container_image=${TARGET_IMAGE}" \
                        "container_status=exited" \
                        "exit_code=1" \
                        "oom_killed=false" \
                        "restart_count=0" \
                        "docker_error=\\"\\""
                }
                rollback() {
                    printf "[TEST] rollback invoked after log timeout\\n"
                    return 0
                }
                deployment_changed=true
                handle_failure 9
            ' bash "${DEPLOY_HELPER}" 2>&1
    )"
    status=$?
    set -e
    [[ "${status}" -eq 9 ]] \
        || { printf 'Expected original status 9, got %s.\n%s\n' "${status}" "${output}" >&2; return 1; }
    assert_before "${output}" \
        'AVISO: os logs recentes do candidato não puderam ser coletados' \
        '[TEST] rollback invoked after log timeout'
    assert_contains "${output}" 'DEPLOY_RESULT=failed-rolled-back'
    grep -Fqx 'recent_logs_unavailable=true' \
        "${fixture_dir}/diagnostics/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.log"
    grep -Fqx 'container_status=exited' \
        "${fixture_dir}/diagnostics/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.log"
    rm -rf -- "${fixture_dir}"
}

test_diagnostics_are_restricted_sanitized_retained_and_persist_after_rollback() {
    local fixture_dir
    local old_commit
    local output

    fixture_dir="$(mktemp -d)"
    mkdir -p "${fixture_dir}/diagnostics"
    chmod 755 "${fixture_dir}/diagnostics"
    printf '%s\n' 'BACKEND_ENV_SENTINEL=backend-env-secret' >"${fixture_dir}/backend.env"
    for old_commit in \
        1111111111111111111111111111111111111111 \
        2222222222222222222222222222222222222222 \
        3333333333333333333333333333333333333333 \
        4444444444444444444444444444444444444444 \
        5555555555555555555555555555555555555555; do
        printf 'old diagnostic %s\n' "${old_commit}" >"${fixture_dir}/diagnostics/${old_commit}.log"
    done
    touch -t 202001010001 "${fixture_dir}/diagnostics/1111111111111111111111111111111111111111.log"
    touch -t 202001010002 "${fixture_dir}/diagnostics/2222222222222222222222222222222222222222.log"
    touch -t 202001010003 "${fixture_dir}/diagnostics/3333333333333333333333333333333333333333.log"
    touch -t 202001010004 "${fixture_dir}/diagnostics/4444444444444444444444444444444444444444.log"
    touch -t 202001010005 "${fixture_dir}/diagnostics/5555555555555555555555555555555555555555.log"
    touch "${fixture_dir}/candidate-active"

    output="$(
        DEPLOY_DIAGNOSTICS_DIR="${fixture_dir}/diagnostics" \
        BACKEND_ENV_FILE="${fixture_dir}/backend.env" \
        TARGET_COMMIT='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
        TARGET_IMAGE='docker.io/pxs00/energiai-backend:sha-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' \
        DOCKER_CALLS_FILE="${fixture_dir}/docker.calls" \
        TIMEOUT_CALLS_FILE="${fixture_dir}/timeout.calls" \
        CANDIDATE_STATE_FILE="${fixture_dir}/candidate-active" \
            bash -c '
                source "$1"
                timeout() {
                    printf "%s\\n" "$*" >>"${TIMEOUT_CALLS_FILE}"
                    shift 3
                    "$@"
                }
                docker() {
                    printf "%s\\n" "$*" >>"${DOCKER_CALLS_FILE}"
                    case "${1:-}" in
                        inspect)
                            [[ -f "${CANDIDATE_STATE_FILE}" ]] || return 1
                            printf "%s\\n" \
                                "container_image=${TARGET_IMAGE}" \
                                "container_status=exited" \
                                "exit_code=1" \
                                "oom_killed=false" \
                                "restart_count=3" \
                                "docker_error=\"failed to start process\""
                            ;;
                        logs)
                            [[ -f "${CANDIDATE_STATE_FILE}" ]] || return 1
                            printf "%s\\n" \
                                "2026-08-05T12:00:00Z startup failed safely" \
                                "JWT_SECRET=top-secret-value" \
                                "Authorization: Bearer hidden-value" \
                                "Cookie: session=hidden-cookie" \
                                "-----BEGIN PRIVATE KEY-----" \
                                "RklDVElUSU9VUy1QRU0tQk9EWQ==" \
                                "-----END PRIVATE KEY-----" \
                                "2026-08-05T12:00:01Z startup context after key"
                            ;;
                        *) return 1 ;;
                    esac
                }
                rollback() {
                    rm -f -- "${CANDIDATE_STATE_FILE}"
                    printf "[TEST] backend recreated with previous image\\n"
                }

                capture_failed_deployment_diagnostics
                diagnostic_file="${DEPLOY_DIAGNOSTICS_DIR}/${TARGET_COMMIT}.log"
                [[ -f "${diagnostic_file}" ]]
                [[ "$(stat -c "%a" -- "${DEPLOY_DIAGNOSTICS_DIR}")" == 700 ]]
                [[ "$(stat -c "%a" -- "${diagnostic_file}")" == 600 ]]
                grep -Eq "^capture_timestamp_utc=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$" "${diagnostic_file}"
                grep -Fqx "target_commit=${TARGET_COMMIT}" "${diagnostic_file}"
                grep -Fqx "target_image=${TARGET_IMAGE}" "${diagnostic_file}"
                grep -Fqx "container_image=${TARGET_IMAGE}" "${diagnostic_file}"
                grep -Fqx "container_status=exited" "${diagnostic_file}"
                grep -Fqx "exit_code=1" "${diagnostic_file}"
                grep -Fqx "oom_killed=false" "${diagnostic_file}"
                grep -Fqx "restart_count=3" "${diagnostic_file}"
                grep -Fqx "docker_error=\"failed to start process\"" "${diagnostic_file}"
                grep -Fqx "recent_logs_tail_lines=200" "${diagnostic_file}"
                grep -Fq "startup failed safely" "${diagnostic_file}"
                grep -Fqx "[REDACTED SENSITIVE DIAGNOSTIC LINE]" "${diagnostic_file}"
                ! grep -Fq "top-secret-value" "${diagnostic_file}"
                ! grep -Fq "hidden-value" "${diagnostic_file}"
                ! grep -Fq "hidden-cookie" "${diagnostic_file}"
                ! grep -Fq "backend-env-secret" "${diagnostic_file}"
                ! grep -Fq -- "-----BEGIN PRIVATE KEY-----" "${diagnostic_file}"
                ! grep -Fq "RklDVElUSU9VUy1QRU0tQk9EWQ==" "${diagnostic_file}"
                ! grep -Fq -- "-----END PRIVATE KEY-----" "${diagnostic_file}"
                grep -Fq "startup context after key" "${diagnostic_file}"
                grep -Fq "logs --timestamps --tail 200 energiai-backend" "${DOCKER_CALLS_FILE}"
                grep -Fq -- "--signal=TERM --kill-after=2s 5s docker inspect --format" "${TIMEOUT_CALLS_FILE}"
                grep -Fq -- "--signal=TERM --kill-after=2s 10s docker logs --timestamps --tail 200 energiai-backend" "${TIMEOUT_CALLS_FILE}"

                rollback
                [[ -f "${diagnostic_file}" ]]
                grep -Fq "startup failed safely" "${diagnostic_file}"
                [[ "$(find "${DEPLOY_DIAGNOSTICS_DIR}" -maxdepth 1 -type f -name "*.log" | wc -l)" -eq 5 ]]
                [[ ! -e "${DEPLOY_DIAGNOSTICS_DIR}/1111111111111111111111111111111111111111.log" ]]
                [[ -f "${diagnostic_file}" ]]
            ' bash "${DEPLOY_HELPER}" 2>&1
    )"
    assert_before "${output}" 'Diagnóstico restrito preservado' '[TEST] backend recreated with previous image'
    rm -rf -- "${fixture_dir}"
}

test_on_error_delegates_to_handle_failure() {
    local output
    local status

    set +e
    output="$(bash -c '
        source "$1"
        handle_failure() {
            printf "[TEST] delegated status=%s\\n" "$1"
            exit "$1"
        }
        trap on_error ERR
        fail_with_status() { return 7; }
        fail_with_status
    ' bash "${DEPLOY_HELPER}" 2>&1)"
    status=$?
    set -e
    [[ "${status}" -eq 7 ]] \
        || { printf 'Expected delegated exit status 7, got %s.\n%s\n' "${status}" "${output}" >&2; return 1; }
    assert_contains "${output}" '[TEST] delegated status=7'
}

test_fail_triggers_on_error
test_deployment_failure_attempts_rollback
test_pre_update_failure_emits_structured_result
test_successful_smoke_does_not_roll_back
test_failed_smoke_rolls_back_and_preserves_status
test_capture_failure_does_not_prevent_rollback
test_non_candidate_container_creates_no_diagnostic
test_log_capture_failure_preserves_metadata_and_emits_warning
test_inspect_timeout_does_not_prevent_rollback_or_replace_failure_status
test_log_timeout_preserves_metadata_and_does_not_prevent_rollback
test_diagnostics_are_restricted_sanitized_retained_and_persist_after_rollback
test_on_error_delegates_to_handle_failure
printf 'PASS test-deploy-backend.sh\n'
