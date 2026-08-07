# Implantação manual

## Validar a instância OCI

Na instância, confirme `cat /etc/os-release`, `uname -m`, `dpkg --print-architecture`, `docker version` e `docker compose version`; arquitetura deve ser `x86_64` e `amd64`.

## Preparar checkout na OCI

```bash
cd /opt/energiai
git clone https://github.com/No-Country-simulation/g9-br-team-09.git repository
cd /opt/energiai/repository
git fetch origin
git checkout --detach <COMMIT_VALIDADO>
```

Este checkout identifica a versão em execução e fornece `compose.yaml` e `Caddyfile`; não execute build de imagem nele na VM.

## Construção externa — máquina local/build host

Na máquina local ou host de build com Docker Buildx, parta de um checkout local validado do commit que será publicado:

```bash
commit_completo="$(git rev-parse HEAD)"
image="docker.io/pxs00/energiai-backend:sha-${commit_completo}"
docker build --platform linux/amd64 -t "${image}" backend/
docker image inspect "${image}" --format '{{.Os}}/{{.Architecture}}'
docker save -o "energiai-backend-${commit_completo}.tar" "${image}"
```

Construa fora da `VM.Standard.E2.1.Micro`; resultado deve ser `linux/amd64`. Use SHA completo, nunca `latest`, `develop` ou SHA curto.

## Publicar, transferir ou carregar

### Na máquina local

```bash
docker pull docker.io/pxs00/energiai-backend:sha-<commit-completo>
docker image inspect docker.io/pxs00/energiai-backend:sha-<commit-completo> --format '{{.Os}}/{{.Architecture}}'
scp "energiai-backend-<commit-completo>.tar" ubuntu@<IP_PUBLICO>:/opt/energiai/images/
# quando necessário: scp -i <CAMINHO_DA_CHAVE_PRIVADA> ...
```

### Na instância OCI

~~~bash
docker load -i /opt/energiai/images/energiai-backend-<commit-completo>.tar
docker image inspect docker.io/pxs00/energiai-backend:sha-<commit-completo> --format '{{.Os}}/{{.Architecture}}'
~~~

Não registre IP ou chave reais. Confirme arquitetura após `docker load`; altere somente `BACKEND_IMAGE` por `sudoedit`, sem alterar credenciais.

## Iniciar, atualizar e rollback

```bash
docker compose --env-file /opt/energiai/config/backend.env -f infra/deploy/oci/compose.yaml config --quiet
docker compose --env-file /opt/energiai/config/backend.env -f infra/deploy/oci/compose.yaml up -d --no-build
docker compose --env-file /opt/energiai/config/backend.env -f infra/deploy/oci/compose.yaml ps
```

Para atualizar, construa/puxe/carregue a nova imagem, valide arquitetura, altere apenas `BACKEND_IMAGE` e repita. Execute [operações](operations.md) e preserve a imagem anterior. Para rollback, use `docker compose ... images backend`, garanta a imagem anterior ou TAR, restaure somente `BACKEND_IMAGE`, valide e recrie sem build; valide health, readiness e ID persistido. Rollback não reverte migrations compatíveis: nunca use `flyway clean`, volumes ou dados Oracle.

## Parada e limpeza

```bash
docker compose --env-file /opt/energiai/config/backend.env -f infra/deploy/oci/compose.yaml down
```

Preserva imagens, ambiente e Oracle. Após rollback, remova somente TAR ou imagem identificada sem dependentes. Nunca remova `backend.env`, `/var/lib/docker`, `/var/lib/containerd`, volumes amplamente ou dados Oracle.
