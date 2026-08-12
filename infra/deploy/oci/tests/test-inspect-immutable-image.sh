#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly INSPECTOR="${SCRIPT_DIR}/../inspect-immutable-image.sh"
readonly COMMIT_SHA='0123456789abcdef0123456789abcdef01234567'
readonly IMAGE="docker.io/pxs00/energiai-backend:sha-${COMMIT_SHA}"
readonly DIGEST='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'

assert_contains() {
    local output="$1"
    local expected="$2"

    [[ "${output}" == *"${expected}"* ]] \
        || { printf 'Expected output to contain: %s\n%s\n' "${expected}" "${output}" >&2; return 1; }
}

mock_docker() {
    case "${TEST_IMAGE_STATE}:${*}" in
        missing:*)
            printf '%s\n' 'manifest unknown' >&2
            return 1
            ;;
        existing:*'{{json .Manifest}}'*)
            printf '{"digest":"%s","manifests":[{"platform":{"os":"linux","architecture":"amd64"}}]}\n' "${DIGEST}"
            ;;
        incompatible:*'{{json .Manifest}}'*)
            printf '{"digest":"%s","manifests":[{"platform":{"os":"linux","architecture":"amd64"}}]}\n' "${DIGEST}"
            ;;
        existing:*'{{json .Image}}'*)
            printf '{"os":"linux","architecture":"amd64","config":{"Labels":{"org.opencontainers.image.revision":"%s"}}}\n' "${COMMIT_SHA}"
            ;;
        incompatible:*'{{json .Image}}'*)
            printf '%s\n' '{"os":"linux","architecture":"amd64","config":{"Labels":{"org.opencontainers.image.revision":"incompatible"}}}'
            ;;
        *)
            printf 'Unexpected mocked docker invocation: %s\n' "$*" >&2
            return 1
            ;;
    esac
}

run_inspector() {
    local state="$1"

    (
        TEST_IMAGE_STATE="${state}"
        source "${INSPECTOR}"
        docker() { mock_docker "$@"; }
        main "${IMAGE}" "${COMMIT_SHA}"
    )
}

test_existing_image_reuses_digest() {
    local output

    output="$(run_inspector existing)"
    assert_contains "${output}" 'exists=true'
    assert_contains "${output}" "digest=${DIGEST}"
}

test_missing_image_allows_build_path() {
    local output

    output="$(run_inspector missing)"
    assert_contains "${output}" 'exists=false'
    grep -Fq -- "if: steps.existing_image.outputs.exists != 'true'" \
        "${SCRIPT_DIR}/../../../../.github/workflows/backend-oci-deploy.yml"
}

test_incompatible_metadata_fails_safely() {
    local output

    if output="$(run_inspector incompatible 2>&1)"; then
        printf 'Expected incompatible metadata to fail.\n%s\n' "${output}" >&2
        return 1
    fi
    assert_contains "${output}" '[FAIL] Os metadados da imagem imutável existente não correspondem ao commit solicitado.'
}

test_existing_image_reuses_digest
test_missing_image_allows_build_path
test_incompatible_metadata_fails_safely
printf 'PASS test-inspect-immutable-image.sh\n'
