#!/usr/bin/env bash

set -Eeuo pipefail

readonly OPERATION="${OPERATION:-}"
readonly REQUESTED_REF="${REQUESTED_REF:-}"
readonly CONFIRMATION="${CONFIRMATION:-}"
readonly OUTPUT_FILE="${GITHUB_OUTPUT:-}"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

require_deployment_confirmation() {
    case "${OPERATION}" in
        deploy|deploy-preview)
            [[ "${CONFIRMATION}" == DEPLOY ]] \
                || fail "${OPERATION} requires confirmation=DEPLOY."
            ;;
        validate) ;;
        *) fail "Unsupported operation: ${OPERATION:-<empty>}." ;;
    esac
}

fetch_develop() {
    git fetch --no-tags --quiet origin \
        '+refs/heads/develop:refs/remotes/origin/develop'
}

resolve_validation_ref() {
    [[ "${REQUESTED_REF}" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$ ]] \
        || fail "The requested ref has unsupported characters."
    git fetch --no-tags --quiet origin "${REQUESTED_REF}"
    git rev-parse --verify 'FETCH_HEAD^{commit}'
}

resolve_preview_commit() {
    local object_type

    [[ "${REQUESTED_REF}" =~ ^[0-9a-f]{40}$ ]] \
        || fail "Preview deployment requires a full lowercase 40-character commit SHA."
    fetch_develop
    object_type="$(git cat-file -t "${REQUESTED_REF}" 2>/dev/null)" \
        || fail "The requested preview SHA does not exist as a commit."
    [[ "${object_type}" == commit ]] \
        || fail "The requested preview SHA does not exist as a commit."
    git merge-base --is-ancestor \
        "${REQUESTED_REF}" refs/remotes/origin/develop \
        || fail "The requested preview SHA is not reachable from origin/develop."
    printf '%s\n' "${REQUESTED_REF}"
}

resolve_production_commit() {
    [[ "${REQUESTED_REF}" == main ]] \
        || fail "Production deployment is allowed only from ref=main."
    git fetch --no-tags --quiet origin main
    local requested_commit
    local main_commit
    requested_commit="$(git rev-parse --verify 'FETCH_HEAD^{commit}')"
    git fetch --no-tags --quiet origin \
        '+refs/heads/main:refs/remotes/origin/main'
    main_commit="$(
        git rev-parse --verify 'refs/remotes/origin/main^{commit}'
    )"
    [[ "${requested_commit}" == "${main_commit}" ]] \
        || fail "Production deployment must use the current origin/main HEAD."
    printf '%s\n' "${requested_commit}"
}

main() {
    local commit_sha
    local source_policy

    [[ -n "${OUTPUT_FILE}" ]] \
        || fail "GITHUB_OUTPUT is required."
    require_deployment_confirmation

    case "${OPERATION}" in
        validate)
            commit_sha="$(resolve_validation_ref)"
            source_policy="validation ref resolved to immutable commit"
            fetch_develop
            ;;
        deploy-preview)
            commit_sha="$(resolve_preview_commit)"
            source_policy="immutable commit reachable from origin/develop"
            ;;
        deploy)
            commit_sha="$(resolve_production_commit)"
            source_policy="current origin/main HEAD"
            fetch_develop
            ;;
    esac

    [[ "${commit_sha}" =~ ^[0-9a-f]{40}$ ]] \
        || fail "The resolved commit is not a full lowercase SHA."
    git checkout --detach --quiet "${commit_sha}"
    [[ "$(git rev-parse --verify 'HEAD^{commit}')" == "${commit_sha}" ]] \
        || fail "Detached checkout does not match the resolved commit."

    {
        printf 'commit_sha=%s\n' "${commit_sha}"
        printf 'requested_ref=%s\n' "${REQUESTED_REF}"
        printf 'source_policy=%s\n' "${source_policy}"
    } >>"${OUTPUT_FILE}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
