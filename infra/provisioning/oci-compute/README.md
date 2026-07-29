# OCI Compute container-host provisioning — Issue #106

This directory prepares the OCI Compute instance provisioned by Issue #104 for
future EnergiAI containers. It installs and configures Docker Engine only; it
does not deploy, pull, build, or run an EnergiAI application service.

## Supported host and prerequisites

Issue #104 is the dependency and architecture source of truth. Run this only
on its Canonical Ubuntu 24.04 LTS instance with `x86_64` kernel architecture,
`amd64` APT architecture, and `ubuntu` as the default administrative user. ARM
and `aarch64` hosts are intentionally rejected.

Connect through the SSH access created by Issue #104, then copy this repository
or this provisioning directory to the instance. The command requires root,
network access to Ubuntu and Docker APT repositories, and a running systemd
host. It does not reboot the instance; it reports if Ubuntu has marked a reboot
as required.

```bash
sudo bash infra/provisioning/oci-compute/install-docker.sh
```

To use an already-existing administrative account other than `ubuntu`:

```bash
sudo ENERGIAI_ADMIN_USER=<existing-user> \
  bash infra/provisioning/oci-compute/install-docker.sh
```

The override cannot be empty, `root`, or a nonexistent user. The script never
creates users, passwords, registry logins, `.env` files, or application
configuration.

## What the script changes

It updates Ubuntu packages without an automatic reboot, installs
`ca-certificates`, `curl`, `iproute2` (for listener validation), and `jq`, and
removes only Docker's documented conflicting package names when they are
installed. It does not remove
`/var/lib/docker` or `/var/lib/containerd`.

Docker is installed from Docker's official HTTPS Ubuntu APT repository:

- key: `/etc/apt/keyrings/docker.asc`
- source: `/etc/apt/sources.list.d/docker.sources`
- repository: `https://download.docker.com/linux/ubuntu`, using the Ubuntu
  suite and APT architecture detected on the host, with the `stable` component

The installed packages are `docker-ce`, `docker-ce-cli`, `containerd.io`,
`docker-buildx-plugin`, and `docker-compose-plugin`. It enables and starts the
Docker systemd service.

The script adds the selected administrative user to the `docker` group only
when needed. **The `docker` group grants root-equivalent privileges.** Log out
and reconnect after the first run, or run `newgrp docker`, before testing
Docker as that non-root user.

Docker remains on its default Unix socket. This provisioning does not configure
the remote Docker TCP API, does not open ports (including 80, 443, 8080, 2375,
or 2376), and does not change OCI networking, UFW, AppArmor, iptables, SSH, or
registry authentication.

## Logging and future directories

`/etc/docker/daemon.json` is created or safely merged to use the `json-file`
driver with `max-size` `10m` and `max-file` `3`. Valid unrelated top-level and
`log-opts` settings are preserved. An existing non-`json-file` log driver, an
invalid JSON file, invalid `log-opts`, or a TCP Docker API is rejected rather
than overridden. A changed existing file is backed up beside it as
`daemon.json.backup.<UTC timestamp>.<pid>`. The candidate configuration is
validated with `dockerd --validate` before replacement; Docker restarts only
when the effective daemon configuration changed. JSON is compared in normalized,
sorted form, so harmless formatting or key-order differences preserve the
existing file bytes and do not create a backup or restart Docker.

The managed Docker directories `/etc/apt/keyrings`, `/etc/apt/sources.list.d`,
and `/etc/docker` are enforced as `root:root` with mode `0755`. Their managed
files are enforced as `root:root` with mode `0644`. Incorrect metadata is
corrected in place without rewriting unchanged contents; a metadata-only
`daemon.json` correction does not create a backup or restart Docker. No
recursive ownership or permission operation is performed.

Daemon log settings apply automatically only to containers created after the
change. Existing containers must be recreated by a later, explicitly authorized
deployment operation to receive the new logging settings.

The following empty future-use directories are created with owner and group of
the selected administrative user and mode `0750`:

| Directory | Future purpose |
| --- | --- |
| `/opt/energiai` | restricted parent for future deployment resources |
| `/opt/energiai/config` | non-secret runtime configuration supplied later |
| `/opt/energiai/logs` | future host-managed application logs |
| `/opt/energiai/data` | future persistent application data |

No configuration, secret, dataset, model, artifact, or placeholder credential
is placed in these directories. A later deployment may refine ownership for
container UIDs/GIDs. Existing non-empty directories with incompatible ownership
or permissions are deliberately left untouched and cause the script to stop.

## Validation

The script validates the platform, the exact installed package versions against
Docker's repository metadata, daemon configuration, service state, CLI,
Compose, Buildx, TCP listener safety, directory state, and the root-run
`hello-world` container. It rejects TCP hosts in `daemon.json`, legacy Docker
defaults, Docker systemd unit/drop-in configuration, effective systemd
properties, and the running `dockerd` command line. It also fails if a
`dockerd` TCP listener is found, including one on a nonstandard port; ports
2375 and 2376 are explicitly checked as prohibited Docker listener ports.
On the instance, these are useful checks:

```bash
cat /etc/os-release
uname -m
dpkg --print-architecture
docker version
docker compose version
docker buildx version
docker info
systemctl is-active docker
systemctl is-enabled docker
sudo dockerd --validate --config-file=/etc/docker/daemon.json
docker run --rm hello-world
```

After reconnecting as the administrative user, perform the non-root smoke
test:

```bash
docker run --rm hello-world
```

Run the provisioning script a second time to verify idempotency. It maintains
one APT source, does not duplicate group membership, retains valid unrelated
daemon settings, does not change populated directories, and avoids a daemon
rewrite or Docker restart when its effective configuration is unchanged.

## Upgrade

Review available package changes, then use normal APT maintenance on the host:

```bash
sudo apt-get update
apt-cache policy docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo apt-get upgrade
```

Re-run the provisioning script afterwards to confirm the repository,
configuration, permissions, and validation checks. Review the Ubuntu reboot
marker and arrange a maintenance-window reboot when required; the script never
reboots automatically.

## Troubleshooting

- **Unsupported OS or architecture:** use the Ubuntu 24.04 `x86_64/amd64`
  instance from Issue #104. Do not change Terraform to select ARM.
- **APT or repository error:** check DNS/HTTPS connectivity, clock, repository
  key and source paths, then run `sudo apt-get update`. Do not use `curl | sh`
  or fall back to Ubuntu's `docker.io` package.
- **Docker fails to start:** inspect `systemctl status docker` and
  `journalctl -u docker --no-pager`; validate the daemon file with the command
  above and restore a known-good backup only after review.
- **Permission denied as a non-root user:** reconnect after group membership
  changes, or use `newgrp docker`; verify with `id -nG`.
- **Invalid `daemon.json` or incompatible log driver:** correct the JSON or
  explicitly decide how an existing non-`json-file` driver should be migrated.
  The script will not replace it automatically.
- **Reboot required:** inspect `/var/run/reboot-required` and schedule a
  deliberate reboot. Do not assume Docker provisioning rebooted the host.

## Rollback and uninstall

Review every command and backup before executing it. Set `admin_user` to the
administrative user selected during provisioning. The following guarded,
non-destructive package rollback retains Docker images, volumes, containers,
containerd state, and all `/opt/energiai` content. Each guard skips only an
expected absent resource; an attempted removal still reports unexpected errors.

```bash
admin_user=ubuntu

if systemctl cat docker >/dev/null 2>&1; then
  sudo systemctl disable --now docker
fi
if id -nG "${admin_user}" | tr ' ' '\n' | grep -Fxq docker; then
  sudo gpasswd -d "${admin_user}" docker
fi
sudo apt-get purge docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin docker-ce-rootless-extras

for managed_file in /etc/apt/sources.list.d/docker.sources /etc/apt/keyrings/docker.asc; do
  if [[ -L "${managed_file}" || ( -e "${managed_file}" && ! -f "${managed_file}" ) ]]; then
    printf 'Refusing to remove unexpected path type: %s\n' "${managed_file}" >&2
    exit 1
  fi
  if [[ -f "${managed_file}" ]]; then
    sudo rm -- "${managed_file}"
  fi
done

# If a reviewed regular backup exists, restore it deliberately, for example:
# sudo install -o root -g root -m 0644 /etc/docker/daemon.json.backup.<timestamp>.<pid> /etc/docker/daemon.json
sudo apt-get update
```

If no daemon backup is available, leave the current daemon configuration in
place and review it manually; do not invent or remove a configuration file.
Removing the APT source/key and restoring a daemon backup are manual, reviewed
operations because they alter host configuration.

**Destructive data deletion (manual and normally unnecessary):** deleting
`/var/lib/docker`, `/var/lib/containerd`, or `/opt/energiai` permanently removes
images, containers, volumes, runtime state, or future application data. This
issue never deletes them; perform any such deletion only after an explicit,
separate backup and data-loss review.

## Out of scope

This work does not modify Terraform, OCI Compute/VCN/subnet/NSG/security lists,
routes, firewall rules, SSH authentication, application code, CI, databases,
registries, DNS, TLS, reverse proxies, or observability. It does not deploy or
run the backend, frontend, FastAPI, database, or any EnergiAI application
container.
