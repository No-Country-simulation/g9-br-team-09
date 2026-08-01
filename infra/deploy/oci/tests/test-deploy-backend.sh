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
        rollback() {
            printf "[TEST] rollback invoked\\n"
            return 0
        }
        deployment_changed=true
        false
    ')"
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
        false
    ')"
    assert_contains "${output}" 'DEPLOY_RESULT=failed-before-update'
    assert_contains "${output}" 'ROLLBACK_ATTEMPTED=no'
    assert_contains "${output}" 'ROLLBACK_RESULT=not-required'
}

test_successful_smoke_does_not_roll_back() {
    local output

    output="$(run_smoke_main_case 0 0)"
    assert_contains "${output}" '[TEST] smoke environment received'
    assert_contains "${output}" 'DEPLOY_RESULT=success'
    assert_contains "${output}" 'SMOKE_TEST_RESULT=passed'
    assert_contains "${output}" 'ROLLBACK_ATTEMPTED=no'
    assert_not_contains "${output}" '[TEST] rollback invoked'
}

test_failed_smoke_rolls_back_and_preserves_status() {
    local output

    output="$(run_smoke_main_case 1 1)"
    assert_contains "${output}" '[TEST] smoke environment received'
    assert_contains "${output}" '[TEST] rollback invoked'
    assert_contains "${output}" 'DEPLOY_RESULT=failed-rolled-back'
    assert_contains "${output}" 'SMOKE_TEST_RESULT=failed'
    assert_contains "${output}" 'ROLLBACK_ATTEMPTED=yes'
    assert_contains "${output}" 'ROLLBACK_RESULT=succeeded'
    assert_not_contains "${output}" 'readonly variable'
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
test_on_error_delegates_to_handle_failure
printf 'PASS test-deploy-backend.sh\n'
