#!/usr/bin/env bash

# Executado na OCI pela workflow da Issue #109. Este script nunca exibe o
# backend.env: ele altera atomicamente apenas BACKEND_IMAGE.
set -Eeuo pipefail

readonly IMAGE_PREFIX="docker.io/pxs00/energiai-backend:sha-"
readonly IMAGE_PATTERN='^docker\.io/pxs00/energiai-backend:sha-[0-9a-f]{40}$'
readonly DIGEST_PATTERN='^sha256:[0-9a-f]{64}$'
readonly READINESS_URL="http://127.0.0.1:8080/api/v1/actuator/health/readiness"
readonly READINESS_ATTEMPTS="${READINESS_ATTEMPTS:-30}"
readonly READINESS_DELAY_SECONDS="${READINESS_DELAY_SECONDS:-5}"

readonly REPOSITORY_DIR="${REPOSITORY_DIR:-/opt/energiai/repository}"
readonly BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/opt/energiai/config/backend.env}"
readonly TARGET_IMAGE="${TARGET_IMAGE:-}"
readonly TARGET_COMMIT="${TARGET_COMMIT:-}"
readonly IMAGE_DIGEST="${IMAGE_DIGEST:-}"
readonly DOCKERHUB_AUTH_FILE="${DOCKERHUB_AUTH_FILE:-}"
readonly EXPECTED_CLASSIFICATION_SOURCE="${EXPECTED_CLASSIFICATION_SOURCE:-}"

docker_config_directory=""
previous_image=""
previous_repository_commit=""
repository_changed=false
environment_temp_file=""
deployment_changed=false
readiness_result="not-run"
smoke_test_result="not-run"
rollback_attempted="no"
rollback_result="not-required"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

require_immutable_image() {
    local image="$1"

    [[ "${image}" =~ ${IMAGE_PATTERN} ]] \
        || fail "A imagem deve usar a tag Docker Hub sha-<commit-completo>."
}

require_commit() {
    local commit="$1"

    [[ "${commit}" =~ ^[0-9a-f]{40}$ ]] \
        || fail "O commit deve ser um SHA completo de 40 caracteres hexadecimais."
}

require_restricted_file() {
    local path="$1"
    local mode

    [[ -f "${path}" && ! -L "${path}" ]] \
        || fail "O arquivo protegido deve ser regular e não pode ser um link."
    mode="$(stat -c '%a' -- "${path}")"
    [[ "${mode}" =~ ^[0-7]{3,4}$ ]] \
        || fail "Não foi possível validar as permissões do arquivo protegido."
    (( (8#${mode} & 8#077) == 0 )) \
        || fail "O arquivo protegido não possui permissões restritivas."
}

require_readiness_policy() {
    if ! [[ "${READINESS_ATTEMPTS}" =~ ^[1-9][0-9]?$ ]] \
        || (( READINESS_ATTEMPTS > 30 )); then
        fail "READINESS_ATTEMPTS deve ser um inteiro entre 1 e 30."
    fi
    if ! [[ "${READINESS_DELAY_SECONDS}" =~ ^[1-9][0-9]?$ ]] \
        || (( READINESS_DELAY_SECONDS > 5 )); then
        fail "READINESS_DELAY_SECONDS deve ser um inteiro entre 1 e 5."
    fi
}

require_dependencies() {
    local command

    for command in docker curl jq git awk mktemp stat chmod chown mv; do
        command -v "${command}" >/dev/null 2>&1 \
            || fail "Dependência obrigatória ausente no host OCI: ${command}."
    done
    docker compose version >/dev/null 2>&1 \
        || fail "Docker Compose não está disponível no host OCI."
}

compose() {
    docker compose \
        --env-file "${BACKEND_ENV_FILE}" \
        -f "${REPOSITORY_DIR}/infra/deploy/oci/compose.yaml" \
        "$@"
}

compose_with_environment_file() {
    local environment_file="$1"
    shift

    docker compose \
        --env-file "${environment_file}" \
        -f "${REPOSITORY_DIR}/infra/deploy/oci/compose.yaml" \
        "$@"
}

read_backend_image() {
    local value

    value="$(awk '
        /^BACKEND_IMAGE=/ {
            matches += 1
            image = substr($0, length("BACKEND_IMAGE=") + 1)
        }
        END {
            if (matches != 1 || image == "") {
                exit 1
            }
            print image
        }
    ' "${BACKEND_ENV_FILE}")" \
        || fail "BACKEND_IMAGE deve existir uma única vez no arquivo de ambiente."

    printf '%s\n' "${value}"
}

replace_backend_image() {
    local replacement="$1"

    require_immutable_image "${replacement}"
    environment_temp_file="$(mktemp "${BACKEND_ENV_FILE}.issue-109.XXXXXX")"
    chmod 600 -- "${environment_temp_file}"

    if ! awk -v replacement="${replacement}" '
        /^BACKEND_IMAGE=/ {
            matches += 1
            if (matches == 1) {
                print "BACKEND_IMAGE=" replacement
            }
            next
        }
        { print }
        END {
            if (matches == 0) {
                print "BACKEND_IMAGE=" replacement
            }
            exit(matches <= 1 ? 0 : 1)
        }
    ' "${BACKEND_ENV_FILE}" >"${environment_temp_file}"; then
        fail "Não foi possível preparar a atualização de BACKEND_IMAGE."
    fi

    chmod --reference="${BACKEND_ENV_FILE}" "${environment_temp_file}"
    chown --reference="${BACKEND_ENV_FILE}" "${environment_temp_file}"
    compose_with_environment_file "${environment_temp_file}" config --quiet
    mv -f -- "${environment_temp_file}" "${BACKEND_ENV_FILE}"
    environment_temp_file=""
}

checkout_commit() {
    local commit="$1"

    require_commit "${commit}"
    git -C "${REPOSITORY_DIR}" fetch --no-tags --quiet origin "${commit}"
    git -C "${REPOSITORY_DIR}" cat-file -e "${commit}^{commit}"
    git -C "${REPOSITORY_DIR}" checkout --detach --quiet "${commit}"
    [[ "$(git -C "${REPOSITORY_DIR}" rev-parse --verify 'HEAD^{commit}')" == "${commit}" ]]
}

wait_for_readiness() {
    local attempt

    readiness_result="pending"
    for ((attempt = 1; attempt <= READINESS_ATTEMPTS; attempt += 1)); do
        if curl --fail --silent --show-error \
            --connect-timeout 5 \
            --max-time 5 \
            "${READINESS_URL}" \
            | jq -e '.status == "UP"' >/dev/null; then
            readiness_result="passed"
            printf '[PASS] Readiness aprovada na tentativa %s/%s.\n' \
                "${attempt}" "${READINESS_ATTEMPTS}"
            return 0
        fi

        printf '[INFO] Readiness pendente (%s/%s).\n' \
            "${attempt}" "${READINESS_ATTEMPTS}"
        if (( attempt < READINESS_ATTEMPTS )); then
            sleep "${READINESS_DELAY_SECONDS}"
        fi
    done

    readiness_result="failed"
    printf '[FAIL] Readiness não atingiu UP dentro do limite.\n' >&2
    return 1
}

login_to_dockerhub() {
    local dockerhub_username
    local dockerhub_token

    [[ -n "${DOCKERHUB_AUTH_FILE}" ]] \
        || fail "O arquivo temporário de credenciais do Docker Hub é obrigatório."
    require_restricted_file "${DOCKERHUB_AUTH_FILE}"

    # O arquivo vem de um diretório remoto 0700 criado pela workflow e contém
    # somente atribuições escapadas com %q; nunca é exibido ou usado em argv.
    # shellcheck disable=SC1090
    source "${DOCKERHUB_AUTH_FILE}"
    dockerhub_username="${DOCKERHUB_USERNAME:-}"
    dockerhub_token="${DOCKERHUB_DEPLOY_TOKEN:-}"
    [[ -n "${dockerhub_username}" && -n "${dockerhub_token}" ]] \
        || fail "As credenciais temporárias do Docker Hub estão incompletas."

    printf '%s' "${dockerhub_token}" | docker login docker.io \
        --username "${dockerhub_username}" \
        --password-stdin >/dev/null
    unset dockerhub_username dockerhub_token DOCKERHUB_USERNAME DOCKERHUB_DEPLOY_TOKEN
}

configure_temporary_docker_config() {
    docker_config_directory="$(mktemp -d /tmp/energiai-docker-config.XXXXXX)" \
        || fail "Não foi possível criar a configuração temporária do Docker."
    chmod 700 -- "${docker_config_directory}"
    export DOCKER_CONFIG="${docker_config_directory}"
}

verify_pulled_image() {
    local image="$1"
    local expected_digest="${2:-}"
    local repository_digests
    local repository_digest
    local expected_bare_digest
    local expected_full_digest
    local digest_found=false
    local platform

    require_immutable_image "${image}"
    repository_digests="$(docker image inspect \
        --format '{{range .RepoDigests}}{{println .}}{{end}}' \
        "${image}")" \
        || fail "A imagem baixada não pode ser inspecionada."
    platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "${image}")" \
        || fail "A plataforma da imagem baixada não pode ser inspecionada."
    [[ "${platform}" == linux/amd64 ]] \
        || fail "A imagem baixada não é linux/amd64."

    if [[ -n "${expected_digest}" ]]; then
        [[ "${expected_digest}" =~ ${DIGEST_PATTERN} ]] \
            || fail "O digest esperado da imagem é inválido."
        expected_bare_digest="pxs00/energiai-backend@${expected_digest}"
        expected_full_digest="docker.io/${expected_bare_digest}"
        while IFS= read -r repository_digest; do
            if [[ "${repository_digest}" == "${expected_bare_digest}" \
                || "${repository_digest}" == "${expected_full_digest}" ]]; then
                digest_found=true
                break
            fi
        done <<<"${repository_digests}"
        [[ "${digest_found}" == true ]] \
            || fail "A imagem baixada não corresponde ao digest publicado."
    fi
}

pull_and_verify_target_image() {
    compose pull backend
    verify_pulled_image "${TARGET_IMAGE}" "${IMAGE_DIGEST}"
}

pull_and_verify_previous_image() {
    compose pull backend
    if [[ "${previous_image}" == "${TARGET_IMAGE}" ]]; then
        verify_pulled_image "${previous_image}" "${IMAGE_DIGEST}"
    else
        verify_pulled_image "${previous_image}"
    fi
}

rollback() {
    printf '[INFO] Iniciando rollback para imagem imutável anterior.\n'

    if ! checkout_commit "${previous_repository_commit}"; then
        return 1
    fi
    if ! replace_backend_image "${previous_image}"; then
        return 1
    fi
    if ! pull_and_verify_previous_image; then
        return 1
    fi
    if ! compose up -d --no-build backend; then
        return 1
    fi
    wait_for_readiness
}

emit_status() {
    printf 'DEPLOY_RESULT=%s\n' "$1"
    printf 'READINESS_RESULT=%s\n' "${readiness_result}"
    printf 'SMOKE_TEST_RESULT=%s\n' "${smoke_test_result}"
    printf 'ROLLBACK_ATTEMPTED=%s\n' "${rollback_attempted}"
    printf 'ROLLBACK_RESULT=%s\n' "${rollback_result}"
    if [[ "${previous_image}" =~ ${IMAGE_PATTERN} ]]; then
        printf 'PREVIOUS_IMAGE=%s\n' "${previous_image}"
    fi
}

on_error() {
    local exit_code="$?"

    trap - ERR
    set +e
    if [[ "${deployment_changed}" == true ]]; then
        rollback_attempted="yes"
        if rollback; then
            rollback_result="succeeded"
            emit_status failed-rolled-back
        else
            rollback_result="failed"
            emit_status failed-rollback-failed
        fi
    elif [[ "${repository_changed}" == true ]]; then
        checkout_commit "${previous_repository_commit}"
        emit_status failed-before-update
    else
        emit_status failed-before-update
    fi
    exit "${exit_code}"
}

cleanup() {
    if [[ -n "${environment_temp_file}" ]]; then
        rm -f -- "${environment_temp_file}"
    fi
    if [[ -n "${docker_config_directory}" ]]; then
        docker logout docker.io >/dev/null 2>&1 || true
        rm -rf -- "${docker_config_directory}"
        unset DOCKER_CONFIG
    fi
    if [[ -n "${DOCKERHUB_AUTH_FILE}" ]]; then
        rm -f -- "${DOCKERHUB_AUTH_FILE}"
    fi
}

main() {
    trap cleanup EXIT

    require_immutable_image "${TARGET_IMAGE}"
    require_commit "${TARGET_COMMIT}"
    [[ "${IMAGE_DIGEST}" =~ ${DIGEST_PATTERN} ]] \
        || fail "O digest publicado deve usar o formato sha256:<64-hex>."
    [[ "${TARGET_IMAGE}" == "${IMAGE_PREFIX}${TARGET_COMMIT}" ]] \
        || fail "A tag da imagem não corresponde ao commit solicitado."
    case "${EXPECTED_CLASSIFICATION_SOURCE}" in
        ""|ML_MODEL|RULE_BASED_FALLBACK) ;;
        *) fail "EXPECTED_CLASSIFICATION_SOURCE é inválida." ;;
    esac
    require_readiness_policy
    require_dependencies
    [[ -d "${REPOSITORY_DIR}/.git" ]] \
        || fail "O checkout OCI esperado não foi encontrado."
    [[ -f "${REPOSITORY_DIR}/infra/deploy/oci/compose.yaml" ]] \
        || fail "O Compose esperado não foi encontrado."
    [[ -f "${REPOSITORY_DIR}/infra/tests/smoke/backend-oci-smoke.sh" ]] \
        || fail "O smoke test esperado não foi encontrado."
    [[ -z "$(git -C "${REPOSITORY_DIR}" status --porcelain)" ]] \
        || fail "O checkout OCI possui alterações locais e não será sobrescrito."
    require_restricted_file "${BACKEND_ENV_FILE}"

    previous_image="$(read_backend_image)"
    require_immutable_image "${previous_image}"
    current_container_image="$(docker inspect --format '{{.Config.Image}}' energiai-backend)" \
        || fail "O container backend atual não pode ser identificado para rollback seguro."
    require_immutable_image "${current_container_image}"
    [[ "${current_container_image}" == "${previous_image}" ]] \
        || fail "O container backend e BACKEND_IMAGE não formam um estado seguro de rollback."
    previous_repository_commit="$(git -C "${REPOSITORY_DIR}" rev-parse --verify 'HEAD^{commit}')"
    require_commit "${previous_repository_commit}"
    [[ "${previous_image}" == "${IMAGE_PREFIX}${previous_repository_commit}" ]] \
        || fail "A imagem atual e o checkout OCI não formam um estado seguro de rollback."
    configure_temporary_docker_config
    compose config --quiet

    trap on_error ERR

    login_to_dockerhub
    checkout_commit "${TARGET_COMMIT}"
    [[ "${TARGET_COMMIT}" == "${previous_repository_commit}" ]] || repository_changed=true
    replace_backend_image "${TARGET_IMAGE}"
    deployment_changed=true
    pull_and_verify_target_image
    compose up -d --no-build backend
    wait_for_readiness

    smoke_test_result="running"
    BASE_URL=http://127.0.0.1:8080/api/v1 \
        REQUEST_TIMEOUT=15 \
        EXPECTED_CLASSIFICATION_SOURCE="${EXPECTED_CLASSIFICATION_SOURCE}" \
        VALIDATED_ARTIFACT="${TARGET_IMAGE}@${IMAGE_DIGEST}" \
        "${REPOSITORY_DIR}/infra/tests/smoke/backend-oci-smoke.sh"
    smoke_test_result="passed"

    deployment_changed=false
    readiness_result="passed"
    emit_status success
}

main "$@"
