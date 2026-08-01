#!/usr/bin/env bash

# Inspeciona uma tag imutável antes de decidir se ela pode ser reutilizada.
set -Eeuo pipefail

readonly IMAGE_PATTERN='^docker\.io/pxs00/energiai-backend:sha-[0-9a-f]{40}$'
readonly DIGEST_PATTERN='^sha256:[0-9a-f]{64}$'

inspection_error_file=""

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

is_missing_image_error() {
    local error_message="$1"

    error_message="${error_message,,}"
    [[ "${error_message}" == *'not found'* \
        || "${error_message}" == *'manifest unknown'* \
        || "${error_message}" == *'name unknown'* ]]
}

cleanup() {
    if [[ -n "${inspection_error_file}" ]]; then
        rm -f -- "${inspection_error_file}"
    fi
}

main() {
    local image="${1:-}"
    local commit_sha="${2:-}"
    local inspection_error
    local manifest
    local metadata
    local digest

    [[ "${image}" =~ ${IMAGE_PATTERN} ]] \
        || fail 'A referência da imagem imutável é inválida.'
    [[ "${commit_sha}" =~ ^[0-9a-f]{40}$ ]] \
        || fail 'O commit deve ser um SHA completo de 40 caracteres hexadecimais.'
    [[ "${image}" == "docker.io/pxs00/energiai-backend:sha-${commit_sha}" ]] \
        || fail 'A tag da imagem imutável não corresponde ao commit solicitado.'

    inspection_error_file="$(mktemp)"
    trap cleanup EXIT
    if ! manifest="$(docker buildx imagetools inspect \
        --format '{{json .Manifest}}' \
        "${image}" 2>"${inspection_error_file}")"; then
        inspection_error="$(<"${inspection_error_file}")"
        if is_missing_image_error "${inspection_error}"; then
            printf 'exists=false\n'
            return 0
        fi
        fail 'Não foi possível verificar a tag imutável existente com segurança.'
    fi

    metadata="$(docker buildx imagetools inspect \
        --format '{{json .Image}}' \
        "${image}")" \
        || fail 'Não foi possível obter os metadados da imagem imutável existente.'
    digest="$(jq -er '.digest' <<<"${manifest}")" \
        || fail 'O manifest da imagem imutável não contém um digest válido.'
    [[ "${digest}" =~ ${DIGEST_PATTERN} ]] \
        || fail 'O digest da imagem imutável existente é inválido.'
    jq -e '
        if (.manifests | type) == "array" then
            any(.manifests[]; .platform.os == "linux" and .platform.architecture == "amd64")
        else
            true
        end
    ' <<<"${manifest}" >/dev/null \
        || fail 'A imagem imutável existente não possui manifest linux/amd64.'
    jq -e --arg commit_sha "${commit_sha}" '
        .os == "linux"
        and .architecture == "amd64"
        and .config.Labels["org.opencontainers.image.revision"] == $commit_sha
    ' <<<"${metadata}" >/dev/null \
        || fail 'Os metadados da imagem imutável existente não correspondem ao commit solicitado.'

    printf 'exists=true\n'
    printf 'digest=%s\n' "${digest}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
