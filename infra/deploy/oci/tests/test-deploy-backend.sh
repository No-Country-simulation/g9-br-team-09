#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly DEPLOY_HELPER="${SCRIPT_DIR}/../deploy-backend.sh"

assert_contains() {
    local output="$1"
    local expected="$2"

    [[ "${output}" == *"${expected}"* ]] \
        || { printf 'Expected output to contain: %s\n%s\n' "${expected}" "${output}" >&2; return 1; }
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

test_fail_triggers_on_error
test_deployment_failure_attempts_rollback
test_pre_update_failure_emits_structured_result
printf 'PASS test-deploy-backend.sh\n'
