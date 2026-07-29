#!/usr/bin/env bash
# EnergiAI OCI Compute container-host provisioning (Issue #106).
# Target: the Ubuntu 24.04 x86_64/amd64 instance provisioned by Issue #104.

set -Eeuo pipefail

readonly SCRIPT_VERSION="1.0.0"
readonly EXPECTED_OS_ID="ubuntu"
readonly EXPECTED_OS_VERSION="24.04"
readonly EXPECTED_KERNEL_ARCH="x86_64"
readonly EXPECTED_APT_ARCH="amd64"
readonly DOCKER_KEYRING_DIR="/etc/apt/keyrings"
readonly DOCKER_KEY_FILE="${DOCKER_KEYRING_DIR}/docker.asc"
readonly DOCKER_SOURCE_FILE="/etc/apt/sources.list.d/docker.sources"
readonly DAEMON_FILE="/etc/docker/daemon.json"
readonly ENERGIAI_ROOT="/opt/energiai"
readonly DOCKER_DEFAULTS_FILE="/etc/default/docker"
readonly DOCKER_DROPIN_DIRECTORY="/etc/systemd/system/docker.service.d"
readonly MANAGED_DIRECTORY_MODE="0755"
readonly MANAGED_FILE_MODE="0644"

ADMIN_USER="${ENERGIAI_ADMIN_USER:-ubuntu}"
ADMIN_GROUP=""
TEMP_DIR=""
ATOMIC_TEMP_FILE=""
CURRENT_STEP="initialization"
DOCKER_DAEMON_CHANGED=false
LAST_DAEMON_BACKUP=""

log() {
  printf '==> %s\n' "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

on_error() {
  local exit_status=$?
  local line_number=$1
  local failed_command=$2

  printf 'ERROR: step "%s" failed at line %s while running: %s\n' \
    "$CURRENT_STEP" "$line_number" "$failed_command" >&2
  exit "$exit_status"
}

cleanup() {
  if [[ -n "${ATOMIC_TEMP_FILE:-}" && -e "${ATOMIC_TEMP_FILE}" ]]; then
    rm -f -- "${ATOMIC_TEMP_FILE}"
  fi
  if [[ -n "${TEMP_DIR:-}" && -d "${TEMP_DIR}" ]]; then
    rm -rf -- "${TEMP_DIR}"
  fi
}

trap 'on_error "$LINENO" "$BASH_COMMAND"' ERR
trap cleanup EXIT

begin_step() {
  CURRENT_STEP=$1
  log "$CURRENT_STEP"
}

enforce_managed_metadata() {
  local path=$1
  local expected_mode=$2
  local owner group mode

  owner=$(stat -c '%u' "${path}")
  group=$(stat -c '%g' "${path}")
  mode=$(stat -c '%a' "${path}")

  if [[ "${owner}" != 0 || "${group}" != 0 ]]; then
    chown root:root -- "${path}"
  fi
  if [[ "${mode}" != "${expected_mode#0}" ]]; then
    chmod "${expected_mode}" -- "${path}"
  fi

  owner=$(stat -c '%u' "${path}")
  group=$(stat -c '%g' "${path}")
  mode=$(stat -c '%a' "${path}")
  [[ "${owner}" == 0 && "${group}" == 0 && "${mode}" == "${expected_mode#0}" ]] || \
    die "Could not enforce root:root ${expected_mode} metadata on '${path}'."
}

ensure_managed_directory() {
  local directory=$1

  if [[ -L "${directory}" ]]; then
    die "Managed directory '${directory}' must not be a symbolic link."
  fi
  if [[ -e "${directory}" ]]; then
    [[ -d "${directory}" ]] || die "Managed path '${directory}' must be a directory."
  else
    install -d -o root -g root -m "${MANAGED_DIRECTORY_MODE}" "${directory}"
    [[ ! -L "${directory}" && -d "${directory}" ]] || \
      die "Managed directory '${directory}' changed type while being created."
  fi
  enforce_managed_metadata "${directory}" "${MANAGED_DIRECTORY_MODE}"
}

require_managed_file_target() {
  local target_file=$1

  ensure_managed_directory "$(dirname "${target_file}")"
  if [[ -L "${target_file}" ]]; then
    die "Managed file '${target_file}' must not be a symbolic link."
  fi
  if [[ -e "${target_file}" ]]; then
    [[ -f "${target_file}" ]] || die "Managed path '${target_file}' must be a regular file."
    enforce_managed_metadata "${target_file}" "${MANAGED_FILE_MODE}"
  fi
}

require_optional_regular_file() {
  local path=$1

  if [[ -L "${path}" ]]; then
    die "Docker configuration path '${path}' must not be a symbolic link."
  fi
  if [[ -e "${path}" ]]; then
    [[ -f "${path}" ]] || die "Docker configuration path '${path}' must be a regular file."
  fi
}

normalized_json_equal() {
  local left_file=$1
  local right_file=$2
  local left_normalized right_normalized

  jq empty "${left_file}" >/dev/null || die "JSON file '${left_file}' is invalid."
  jq empty "${right_file}" >/dev/null || die "JSON file '${right_file}' is invalid."
  left_normalized=$(mktemp "${TEMP_DIR}/normalized-left.XXXXXX")
  right_normalized=$(mktemp "${TEMP_DIR}/normalized-right.XXXXXX")
  jq -S -c . "${left_file}" >"${left_normalized}"
  jq -S -c . "${right_file}" >"${right_normalized}"
  cmp -s "${left_normalized}" "${right_normalized}"
}

require_root() {
  begin_step "checking root privileges"
  [[ "${EUID}" -eq 0 ]] || die "This script must be run as root (for example: sudo bash $0)."
}

validate_admin_user() {
  begin_step "validating administrative user"
  [[ -n "${ADMIN_USER}" ]] || die "ENERGIAI_ADMIN_USER must not be empty."
  [[ "${ADMIN_USER}" != "root" ]] || die "ENERGIAI_ADMIN_USER must not be root."
  getent passwd "${ADMIN_USER}" >/dev/null || die "Administrative user '${ADMIN_USER}' does not exist."
  ADMIN_GROUP=$(id -gn "${ADMIN_USER}")
  [[ -n "${ADMIN_GROUP}" ]] || die "Could not determine the primary group for '${ADMIN_USER}'."
}

validate_platform() {
  local detected_kernel_arch detected_apt_arch

  begin_step "validating Ubuntu 24.04 x86_64/amd64 platform"
  [[ -r /etc/os-release ]] || die "Missing required platform file: /etc/os-release."
  # shellcheck disable=SC1091
  . /etc/os-release

  [[ "${ID:-}" == "${EXPECTED_OS_ID}" ]] || die "Unsupported OS ID '${ID:-unknown}'; expected '${EXPECTED_OS_ID}'."
  [[ "${VERSION_ID:-}" == "${EXPECTED_OS_VERSION}" ]] || die "Unsupported Ubuntu version '${VERSION_ID:-unknown}'; expected '${EXPECTED_OS_VERSION}'."

  detected_kernel_arch=$(uname -m)
  [[ "${detected_kernel_arch}" == "${EXPECTED_KERNEL_ARCH}" ]] || \
    die "Unsupported kernel architecture '${detected_kernel_arch}'; expected '${EXPECTED_KERNEL_ARCH}'."

  detected_apt_arch=$(dpkg --print-architecture)
  [[ "${detected_apt_arch}" == "${EXPECTED_APT_ARCH}" ]] || \
    die "Unsupported APT architecture '${detected_apt_arch}'; expected '${EXPECTED_APT_ARCH}'."

  command -v systemctl >/dev/null || die "systemd tooling is unavailable: systemctl was not found."
  [[ -d /run/systemd/system ]] || die "systemd is not running; this host is unsupported."
}

initialize_temporary_directory() {
  begin_step "creating secure temporary workspace"
  TEMP_DIR=$(mktemp -d /tmp/energiai-oci-compute.XXXXXX)
  chmod 0700 "${TEMP_DIR}"
}

update_system_packages() {
  begin_step "updating Ubuntu packages without rebooting"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get -y upgrade
  apt-get install -y ca-certificates curl iproute2 jq

  if [[ -e /var/run/reboot-required ]]; then
    log "A reboot is required by an Ubuntu package update; the script will not reboot automatically."
  fi
}

remove_conflicting_packages() {
  local package
  local -a installed_conflicts=()
  local -a conflicting_packages=(
    docker.io docker-compose docker-compose-v2 docker-doc podman-docker containerd runc
  )

  begin_step "removing Docker-conflicting packages when present"
  for package in "${conflicting_packages[@]}"; do
    if dpkg-query -W -f='${db:Status-Status}' "${package}" 2>/dev/null | grep -qx 'installed'; then
      installed_conflicts+=("${package}")
    fi
  done

  if ((${#installed_conflicts[@]} > 0)); then
    log "Removing conflicting packages: ${installed_conflicts[*]} (Docker data directories are retained)."
    apt-get remove -y "${installed_conflicts[@]}"
  else
    log "No Docker-conflicting packages are installed."
  fi
}

replace_file_if_changed() {
  local source_file=$1
  local target_file=$2
  local file_mode=$3
  local target_dir replacement_file

  [[ -f "${source_file}" && ! -L "${source_file}" ]] || \
    die "Replacement source '${source_file}' must be a regular file."
  ensure_managed_directory "$(dirname "${target_file}")"
  require_managed_file_target "${target_file}"

  if [[ -e "${target_file}" ]] && cmp -s "${source_file}" "${target_file}"; then
    return 0
  fi

  target_dir=$(dirname "${target_file}")
  ensure_managed_directory "${target_dir}"
  require_managed_file_target "${target_file}"
  replacement_file=$(mktemp "${target_dir}/.$(basename "${target_file}").XXXXXX")
  ATOMIC_TEMP_FILE=${replacement_file}
  install -o root -g root -m "${file_mode}" "${source_file}" "${replacement_file}"
  require_managed_file_target "${target_file}"
  mv -f "${replacement_file}" "${target_file}"
  ATOMIC_TEMP_FILE=""
  require_managed_file_target "${target_file}"
  return 0
}

configure_docker_repository() {
  local key_candidate source_candidate suite apt_arch

  begin_step "configuring Docker's official Ubuntu APT repository"
  ensure_managed_directory "${DOCKER_KEYRING_DIR}"
  ensure_managed_directory /etc/apt/sources.list.d
  require_managed_file_target "${DOCKER_KEY_FILE}"
  require_managed_file_target "${DOCKER_SOURCE_FILE}"

  key_candidate=$(mktemp "${TEMP_DIR}/docker.asc.XXXXXX")
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o "${key_candidate}"
  replace_file_if_changed "${key_candidate}" "${DOCKER_KEY_FILE}" 0644

  # shellcheck disable=SC1091
  . /etc/os-release
  suite=${VERSION_CODENAME:-}
  [[ -n "${suite}" ]] || die "Ubuntu suite could not be derived from /etc/os-release."
  apt_arch=$(dpkg --print-architecture)
  source_candidate=$(mktemp "${TEMP_DIR}/docker.sources.XXXXXX")
  printf '%s\n' \
    'Types: deb' \
    'URIs: https://download.docker.com/linux/ubuntu' \
    "Suites: ${suite}" \
    'Components: stable' \
    "Architectures: ${apt_arch}" \
    "Signed-By: ${DOCKER_KEY_FILE}" >"${source_candidate}"
  replace_file_if_changed "${source_candidate}" "${DOCKER_SOURCE_FILE}" 0644
  apt-get update
}

install_docker_packages() {
  local package
  local -a docker_packages=(
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  )

  begin_step "installing Docker Engine and CLI plugins from Docker's repository"
  apt-get install -y "${docker_packages[@]}"

  for package in "${docker_packages[@]}"; do
    verify_installed_package_origin "${package}"
  done
}

verify_installed_package_origin() {
  local package=$1
  local installed_version

  installed_version=$(dpkg-query -W -f='${db:Status-Status}\t${Version}' "${package}" 2>/dev/null) || \
    die "Required package '${package}' is not installed."
  [[ "${installed_version}" == installed$'\t'* ]] || die "Required package '${package}' is not installed."
  installed_version=${installed_version#*$'\t'}

  apt-cache madison "${package}" | awk -F '|' -v expected_version="${installed_version}" '
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    trim($2) == expected_version && $3 ~ /^ https:\/\/download\.docker\.com\/linux\/ubuntu/ { found = 1 }
    END { exit(found ? 0 : 1) }
  ' || die "Installed package '${package}' version '${installed_version}' is not available from Docker's official Ubuntu repository."
}

reject_tcp_docker_api() {
  local configuration_file=$1
  local jq_status

  if jq -e '
    .hosts? as $hosts |
    if $hosts == null then false
    elif ($hosts | type) == "string" then ($hosts | startswith("tcp://"))
    elif ($hosts | type) == "array" then any($hosts[]; type == "string" and startswith("tcp://"))
    else true end
  ' "${configuration_file}" >/dev/null; then
    die "Docker TCP API configuration is prohibited; keep Docker on its Unix socket."
  else
    jq_status=$?
    [[ "${jq_status}" -eq 1 ]] || \
      die "Could not inspect Docker TCP API configuration in '${configuration_file}'."
  fi
  return 0
}

text_contains_tcp_host_argument() {
  local text=$1

  [[ "${text}" =~ (^|[[:space:]])(-H|--host)(=|[[:space:]])+tcp:// ]] || \
    [[ "${text}" =~ (^|[[:space:]=])DOCKER_HOST=tcp:// ]]
}

arguments_contain_tcp_host() {
  local argument
  local expect_host_argument=false

  for argument in "$@"; do
    if [[ "${expect_host_argument}" == true ]]; then
      [[ "${argument}" == tcp://* ]] && return 0
      expect_host_argument=false
    fi
    case "${argument}" in
      -H|--host)
        expect_host_argument=true
        ;;
      -H=tcp://*|--host=tcp://*|-Htcp://*)
        return 0
        ;;
    esac
  done
  return 1
}

inspect_optional_docker_configuration_file() {
  local path=$1
  local content

  require_optional_regular_file "${path}"
  if [[ ! -e "${path}" ]]; then
    return 0
  fi
  content=$(<"${path}")
  if text_contains_tcp_host_argument "${content}"; then
    die "Docker TCP API configuration is prohibited in '${path}'."
  fi
  return 0
}

inspect_docker_dropins() {
  local dropin
  local -a dropins=()

  if [[ -L "${DOCKER_DROPIN_DIRECTORY}" ]]; then
    die "Docker drop-in directory '${DOCKER_DROPIN_DIRECTORY}' must not be a symbolic link."
  fi
  if [[ ! -e "${DOCKER_DROPIN_DIRECTORY}" ]]; then
    return 0
  fi
  [[ -d "${DOCKER_DROPIN_DIRECTORY}" ]] || \
    die "Docker drop-in path '${DOCKER_DROPIN_DIRECTORY}' must be a directory."
  if ! find "${DOCKER_DROPIN_DIRECTORY}" -mindepth 1 -maxdepth 1 -print0 >/dev/null; then
    die "Could not inspect Docker drop-in directory '${DOCKER_DROPIN_DIRECTORY}'."
  fi
  while IFS= read -r -d '' dropin; do
    dropins+=("${dropin}")
  done < <(find "${DOCKER_DROPIN_DIRECTORY}" -mindepth 1 -maxdepth 1 -print0)
  for dropin in "${dropins[@]}"; do
    require_optional_regular_file "${dropin}"
    inspect_optional_docker_configuration_file "${dropin}"
  done
  return 0
}

inspect_effective_docker_service_configuration() {
  local systemctl_cat_output systemctl_show_output

  inspect_optional_docker_configuration_file "${DOCKER_DEFAULTS_FILE}"
  inspect_docker_dropins
  inspect_optional_docker_configuration_file /lib/systemd/system/docker.service
  inspect_optional_docker_configuration_file /usr/lib/systemd/system/docker.service

  systemctl_cat_output=$(systemctl cat docker) || die "Could not inspect the effective Docker systemd unit."
  systemctl_show_output=$(systemctl show docker --property=ExecStart --property=Environment --property=EnvironmentFiles) || \
    die "Could not inspect the effective Docker systemd configuration."
  if text_contains_tcp_host_argument "${systemctl_cat_output}"; then
    die "Docker systemd configuration enables a prohibited TCP API listener."
  fi
  if text_contains_tcp_host_argument "${systemctl_show_output}"; then
    die "Effective Docker systemd configuration enables a prohibited TCP API listener."
  fi
  return 0
}

inspect_running_dockerd_arguments() {
  local pid argument
  local -a daemon_arguments=()
  local -a daemon_pids=()

  command -v pgrep >/dev/null || die "Cannot inspect dockerd arguments because 'pgrep' is unavailable."
  while IFS= read -r pid; do
    daemon_pids+=("${pid}")
  done < <(pgrep -x dockerd)
  ((${#daemon_pids[@]} > 0)) || die "Docker is active but no dockerd process was found."

  for pid in "${daemon_pids[@]}"; do
    [[ -r "/proc/${pid}/cmdline" ]] || die "Cannot inspect dockerd process ${pid} command line."
    daemon_arguments=()
    while IFS= read -r -d '' argument; do
      daemon_arguments+=("${argument}")
    done <"/proc/${pid}/cmdline"
    if arguments_contain_tcp_host "${daemon_arguments[@]}"; then
      die "Running dockerd process ${pid} has a prohibited TCP host argument."
    fi
  done
  return 0
}

configure_docker_daemon() {
  local candidate existing_log_driver backup_file

  begin_step "merging bounded Docker json-file log rotation"
  ensure_managed_directory /etc/docker
  require_managed_file_target "${DAEMON_FILE}"
  inspect_effective_docker_service_configuration
  candidate=$(mktemp "${TEMP_DIR}/daemon.json.XXXXXX")

  if [[ -e "${DAEMON_FILE}" ]]; then
    jq empty "${DAEMON_FILE}" >/dev/null || die "Existing ${DAEMON_FILE} is not valid JSON; fix it manually before rerunning."
    jq -e 'type == "object"' "${DAEMON_FILE}" >/dev/null || \
      die "Existing ${DAEMON_FILE} must contain a JSON object."
    if jq -e 'has("log-driver")' "${DAEMON_FILE}" >/dev/null; then
      existing_log_driver=$(jq -r '.["log-driver"]' "${DAEMON_FILE}")
      [[ "${existing_log_driver}" == "json-file" ]] || \
        die "Existing Docker log-driver '${existing_log_driver}' is incompatible; expected 'json-file'."
    fi
    jq -e 'if has("log-opts") then (."log-opts" | type == "object") else true end' "${DAEMON_FILE}" >/dev/null || \
      die "Existing Docker log-opts must be a JSON object."
    jq '. + {"log-driver": "json-file", "log-opts": ((."log-opts" // {}) + {"max-size": "10m", "max-file": "3"})}' \
      "${DAEMON_FILE}" >"${candidate}"
  else
    jq -n '{"log-driver": "json-file", "log-opts": {"max-size": "10m", "max-file": "3"}}' >"${candidate}"
  fi

  reject_tcp_docker_api "${candidate}"
  dockerd --validate --config-file="${candidate}"

  if [[ -e "${DAEMON_FILE}" ]] && normalized_json_equal "${candidate}" "${DAEMON_FILE}"; then
    log "${DAEMON_FILE} already has the required effective configuration; no rewrite or restart is needed."
    return
  fi

  if [[ -e "${DAEMON_FILE}" ]]; then
    require_managed_file_target "${DAEMON_FILE}"
    backup_file="${DAEMON_FILE}.backup.$(date -u +%Y%m%dT%H%M%SZ).$$"
    cp -p "${DAEMON_FILE}" "${backup_file}"
    LAST_DAEMON_BACKUP=${backup_file}
    log "Backed up the changed daemon configuration to ${backup_file}."
  fi
  replace_file_if_changed "${candidate}" "${DAEMON_FILE}" 0644
  DOCKER_DAEMON_CHANGED=true
}

configure_admin_access() {
  begin_step "configuring administrative Docker group access"
  if id -nG "${ADMIN_USER}" | tr ' ' '\n' | grep -Fxq docker; then
    log "User '${ADMIN_USER}' is already a member of the docker group."
  else
    usermod -aG docker "${ADMIN_USER}"
    log "Added '${ADMIN_USER}' to the docker group; reconnect or run 'newgrp docker' before non-root Docker use."
  fi
}

create_energiai_directories() {
  local directory current_state expected_state
  local -a directories=(
    "${ENERGIAI_ROOT}"
    "${ENERGIAI_ROOT}/config"
    "${ENERGIAI_ROOT}/logs"
    "${ENERGIAI_ROOT}/data"
  )

  begin_step "creating secure future EnergiAI directories"
  for directory in "${directories[@]}"; do
    expected_state="${ADMIN_USER} ${ADMIN_GROUP} 750"
    if [[ ! -e "${directory}" ]]; then
      install -d -o "${ADMIN_USER}" -g "${ADMIN_GROUP}" -m 0750 "${directory}"
    elif [[ ! -d "${directory}" ]]; then
      die "Expected directory '${directory}' is an existing non-directory path."
    else
      current_state=$(stat -c '%U %G %a' "${directory}")
      if [[ "${current_state}" != "${expected_state}" ]]; then
        if find "${directory}" -mindepth 1 -print -quit | grep -q .; then
          die "Existing non-empty directory '${directory}' has state '${current_state}'; refusing to alter it."
        fi
        chown "${ADMIN_USER}:${ADMIN_GROUP}" "${directory}"
        chmod 0750 "${directory}"
      fi
    fi
  done
}

enable_docker_service() {
  begin_step "enabling and starting Docker with systemd"
  if [[ "${DOCKER_DAEMON_CHANGED}" == true ]]; then
    systemctl restart docker
  fi
  systemctl enable --now docker
}

validate_no_tcp_listener() {
  local listener_output docker_listener_output grep_status

  command -v ss >/dev/null || die "Cannot validate Docker TCP listeners because 'ss' is unavailable."
  listener_output=$(ss -ltnpH) || die "Could not inspect active TCP listeners."
  if docker_listener_output=$(printf '%s\n' "${listener_output}" | grep -F '(("dockerd"'); then
    :
  else
    grep_status=$?
    [[ "${grep_status}" -eq 1 ]] || die "Could not filter Docker TCP listeners."
    docker_listener_output=""
  fi
  if [[ -n "${docker_listener_output}" ]]; then
    if printf '%s\n' "${docker_listener_output}" | awk '{print $4}' | grep -Eq '(:2375|:2376)$'; then
      die "Docker has a prohibited TCP listener on port 2375 or 2376."
    fi
    die "Docker has a prohibited TCP listener on a nonstandard port."
  fi
  return 0
}

validate_energiai_directories() {
  local directory actual_state expected_state
  local -a directories=(
    "${ENERGIAI_ROOT}"
    "${ENERGIAI_ROOT}/config"
    "${ENERGIAI_ROOT}/logs"
    "${ENERGIAI_ROOT}/data"
  )

  expected_state="${ADMIN_USER} ${ADMIN_GROUP} 750"
  for directory in "${directories[@]}"; do
    actual_state=$(stat -c '%U %G %a' "${directory}")
    [[ "${actual_state}" == "${expected_state}" ]] || \
      die "Directory '${directory}' has '${actual_state}'; expected '${expected_state}'."
  done
}

validate_installation() {
  local package
  local -a docker_packages=(
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  )

  begin_step "validating Docker installation and host configuration"
  cat /etc/os-release
  uname -m
  dpkg --print-architecture
  [[ "$(uname -m)" == "${EXPECTED_KERNEL_ARCH}" ]] || die "Kernel architecture validation failed."
  [[ "$(dpkg --print-architecture)" == "${EXPECTED_APT_ARCH}" ]] || die "APT architecture validation failed."
  systemctl is-active --quiet docker || die "Docker service is not active."
  systemctl is-enabled --quiet docker || die "Docker service is not enabled."
  dockerd --validate --config-file="${DAEMON_FILE}"
  docker version
  docker compose version
  docker buildx version
  docker info
  for package in "${docker_packages[@]}"; do
    dpkg-query -W -f='${Package} ${Version}\n' "${package}"
    verify_installed_package_origin "${package}"
  done
  [[ "$(jq -r '.["log-driver"]' "${DAEMON_FILE}")" == "json-file" ]] || die "Docker log-driver is not json-file."
  [[ "$(jq -r '."log-opts"."max-size"' "${DAEMON_FILE}")" == "10m" ]] || die "Docker max-size is not 10m."
  [[ "$(jq -r '."log-opts"."max-file"' "${DAEMON_FILE}")" == "3" ]] || die "Docker max-file is not 3."
  reject_tcp_docker_api "${DAEMON_FILE}"
  inspect_effective_docker_service_configuration
  inspect_running_dockerd_arguments
  validate_no_tcp_listener
  id -nG "${ADMIN_USER}" | tr ' ' '\n' | grep -Fxq docker || die "User '${ADMIN_USER}' is not in the docker group."
  validate_energiai_directories
  docker run --rm hello-world
  log "Docker provisioning ${SCRIPT_VERSION} completed successfully."
  if [[ -n "${LAST_DAEMON_BACKUP}" ]]; then
    log "Previous daemon configuration backup: ${LAST_DAEMON_BACKUP}"
  fi
}

main() {
  require_root
  validate_admin_user
  validate_platform
  initialize_temporary_directory
  update_system_packages
  remove_conflicting_packages
  configure_docker_repository
  install_docker_packages
  configure_docker_daemon
  configure_admin_access
  create_energiai_directories
  enable_docker_service
  validate_installation
}

main "$@"
