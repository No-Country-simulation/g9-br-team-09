#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly RESOLVER="${SCRIPT_DIR}/../resolve-deploy-source.sh"

fixture_root=""
remote_repository=""
seed_repository=""
develop_commit=""
main_commit=""
outside_commit=""

cleanup() {
    if [[ -n "${fixture_root}" ]]; then
        rm -rf -- "${fixture_root}"
    fi
}

assert_contains() {
    local output="$1"
    local expected="$2"

    [[ "${output}" == *"${expected}"* ]] \
        || { printf 'Expected output to contain: %s\n%s\n' "${expected}" "${output}" >&2; return 1; }
}

create_fixture() {
    fixture_root="$(mktemp -d)"
    remote_repository="${fixture_root}/origin.git"
    seed_repository="${fixture_root}/seed"
    git init --bare --quiet "${remote_repository}"
    git init --quiet -b main "${seed_repository}"
    git -C "${seed_repository}" config user.name 'Deploy source test'
    git -C "${seed_repository}" config user.email 'deploy-source@example.invalid'
    git -C "${seed_repository}" commit --quiet --allow-empty -m 'main base'
    main_commit="$(git -C "${seed_repository}" rev-parse HEAD)"
    git -C "${seed_repository}" remote add origin "${remote_repository}"
    git -C "${seed_repository}" push --quiet origin main
    git -C "${seed_repository}" tag validation-tag "${main_commit}"
    git -C "${seed_repository}" push --quiet origin validation-tag

    git -C "${seed_repository}" switch --quiet -c develop
    git -C "${seed_repository}" commit --quiet --allow-empty -m 'develop commit'
    develop_commit="$(git -C "${seed_repository}" rev-parse HEAD)"
    git -C "${seed_repository}" push --quiet origin develop

    git -C "${seed_repository}" switch --quiet main
    git -C "${seed_repository}" switch --quiet -c outside
    git -C "${seed_repository}" commit --quiet --allow-empty -m 'outside commit'
    outside_commit="$(git -C "${seed_repository}" rev-parse HEAD)"
    git -C "${seed_repository}" push --quiet origin outside
}

clone_fixture() {
    local destination="$1"

    git clone --quiet --branch develop "${remote_repository}" "${destination}"
}

run_success_case() {
    local operation="$1"
    local requested_ref="$2"
    local confirmation="$3"
    local repository
    local output_file

    repository="$(mktemp -d "${fixture_root}/case.XXXXXX")"
    rmdir -- "${repository}"
    output_file="${repository}.output"
    clone_fixture "${repository}"
    (
        cd "${repository}"
        OPERATION="${operation}" \
        REQUESTED_REF="${requested_ref}" \
        CONFIRMATION="${confirmation}" \
        GITHUB_OUTPUT="${output_file}" \
            bash "${RESOLVER}"
    )
    printf '%s\n' "${repository}" "${output_file}"
}

run_failure_case() {
    local operation="$1"
    local requested_ref="$2"
    local confirmation="$3"
    local fetch_outside="${4:-false}"
    local repository
    local output_file
    local output
    local status

    repository="$(mktemp -d "${fixture_root}/failure.XXXXXX")"
    rmdir -- "${repository}"
    output_file="${repository}.output"
    clone_fixture "${repository}"
    if [[ "${fetch_outside}" == true ]]; then
        git -C "${repository}" fetch --quiet origin outside
    fi
    set +e
    output="$({
        cd "${repository}" && \
        OPERATION="${operation}" \
        REQUESTED_REF="${requested_ref}" \
        CONFIRMATION="${confirmation}" \
        GITHUB_OUTPUT="${output_file}" \
            bash "${RESOLVER}"
    } 2>&1)"
    status=$?
    set -e
    [[ "${status}" -eq 1 ]] \
        || { printf 'Expected exit status 1, got %s.\n%s\n' "${status}" "${output}" >&2; return 1; }
    printf '%s' "${output}"
}

test_validate_accepts_branch_without_confirmation() {
    local paths
    local repository
    local output_file

    paths="$(run_success_case validate develop '')"
    repository="$(sed -n '1p' <<<"${paths}")"
    output_file="$(sed -n '2p' <<<"${paths}")"
    grep -Fxq "commit_sha=${develop_commit}" "${output_file}"
    grep -Fxq 'source_policy=validation ref resolved to immutable commit' "${output_file}"
    [[ "$(git -C "${repository}" rev-parse HEAD)" == "${develop_commit}" ]]
    [[ "$(git -C "${repository}" symbolic-ref -q HEAD || true)" == "" ]]
}

test_validate_accepts_tag_and_commit() {
    local paths
    local output_file

    paths="$(run_success_case validate validation-tag '')"
    output_file="$(sed -n '2p' <<<"${paths}")"
    grep -Fxq "commit_sha=${main_commit}" "${output_file}"

    paths="$(run_success_case validate "${develop_commit}" '')"
    output_file="$(sed -n '2p' <<<"${paths}")"
    grep -Fxq "commit_sha=${develop_commit}" "${output_file}"
}

test_preview_accepts_full_develop_sha() {
    local paths
    local repository
    local output_file

    paths="$(run_success_case deploy-preview "${develop_commit}" DEPLOY)"
    repository="$(sed -n '1p' <<<"${paths}")"
    output_file="$(sed -n '2p' <<<"${paths}")"
    grep -Fxq "commit_sha=${develop_commit}" "${output_file}"
    grep -Fxq 'source_policy=immutable commit reachable from origin/develop' "${output_file}"
    [[ "$(git -C "${repository}" rev-parse HEAD)" == "${develop_commit}" ]]
    [[ "$(git -C "${repository}" symbolic-ref -q HEAD || true)" == "" ]]
}

test_preview_rejects_abbreviated_sha() {
    local output

    output="$(run_failure_case deploy-preview "${develop_commit:0:12}" DEPLOY)"
    assert_contains "${output}" 'Preview deployment requires a full lowercase 40-character commit SHA.'
}

test_preview_rejects_branch_name() {
    local output

    output="$(run_failure_case deploy-preview develop DEPLOY)"
    assert_contains "${output}" 'Preview deployment requires a full lowercase 40-character commit SHA.'
}

test_preview_rejects_uppercase_sha() {
    local output

    output="$(run_failure_case deploy-preview "${develop_commit^^}" DEPLOY)"
    assert_contains "${output}" 'Preview deployment requires a full lowercase 40-character commit SHA.'
}

test_preview_rejects_unknown_commit() {
    local output

    output="$(run_failure_case deploy-preview '0000000000000000000000000000000000000000' DEPLOY)"
    assert_contains "${output}" 'The requested preview SHA does not exist as a commit.'
}

test_preview_rejects_commit_outside_develop() {
    local output

    output="$(run_failure_case deploy-preview "${outside_commit}" DEPLOY true)"
    assert_contains "${output}" 'The requested preview SHA is not reachable from origin/develop.'
}

test_preview_requires_confirmation() {
    local output

    output="$(run_failure_case deploy-preview "${develop_commit}" '')"
    assert_contains "${output}" 'deploy-preview requires confirmation=DEPLOY.'
}

test_production_requires_current_main() {
    local paths
    local repository
    local output_file
    local output

    paths="$(run_success_case deploy main DEPLOY)"
    repository="$(sed -n '1p' <<<"${paths}")"
    output_file="$(sed -n '2p' <<<"${paths}")"
    grep -Fxq "commit_sha=${main_commit}" "${output_file}"
    grep -Fxq 'source_policy=current origin/main HEAD' "${output_file}"
    [[ "$(git -C "${repository}" rev-parse HEAD)" == "${main_commit}" ]]

    output="$(run_failure_case deploy "${main_commit}" DEPLOY)"
    assert_contains "${output}" 'Production deployment is allowed only from ref=main.'
}

test_production_requires_confirmation() {
    local output

    output="$(run_failure_case deploy main '')"
    assert_contains "${output}" 'deploy requires confirmation=DEPLOY.'
}

trap cleanup EXIT
create_fixture
test_validate_accepts_branch_without_confirmation
test_validate_accepts_tag_and_commit
test_preview_accepts_full_develop_sha
test_preview_rejects_abbreviated_sha
test_preview_rejects_branch_name
test_preview_rejects_uppercase_sha
test_preview_rejects_unknown_commit
test_preview_rejects_commit_outside_develop
test_preview_requires_confirmation
test_production_requires_current_main
test_production_requires_confirmation
printf 'PASS test-resolve-deploy-source.sh\n'
