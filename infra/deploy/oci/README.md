# OCI Compute backend deployment — Issues #107, #109, and #110

This runbook covers the manual, reproducible, and reversible EnergiAI Spring
Boot deployment, the manually dispatched automation from Issue #109, and the
public HTTPS reverse proxy from Issue #110. Terraform provisions the OCI
network separately. This deployment does not install Docker, deploy FastAPI,
or expose backend port `8080` to the internet.

## Escopo, dependências e arquitetura

O fluxo depende das entregas anteriores:

- Issue #104: instância `VM.Standard.E2.1.Micro`, Canonical Ubuntu 24.04 LTS, `x86_64/amd64`, rede e acesso SSH;
- Issue #105: health geral, liveness de processo e readiness com Oracle obrigatório;
- Issue #106: Docker Engine, Compose, Buildx, rotação de logs e diretórios `/opt/energiai`.
- Issue #108: smoke tests de contrato reutilizados após cada atualização.

A imagem é construída fora da VM para `linux/amd64`, identificada por uma tag imutável completa e publicada no repositório Docker Hub existente `docker.io/pxs00/energiai-backend`. A alternativa manual de transferir um TAR permanece disponível para recuperação controlada. Na OCI o Compose apenas inicia a imagem com `--no-build`; a `VM.Standard.E2.1.Micro` não faz build de imagens.

Compose runs two services on the existing `energiai-oci` bridge network:

- `backend` keeps the host binding `127.0.0.1:8080:8080`, so local smoke tests
  and the SSH tunnel continue to work without making port `8080` public;
- `caddy` uses the pinned `caddy:2.11.4-alpine` image, publishes only TCP ports
  `80` and `443`, and proxies to `backend:8080` over the Docker network.

Caddy stores certificate and runtime state in the named `caddy_data` and
`caddy_config` volumes. Oracle Autonomous Database remains mandatory for
backend readiness. FastAPI remains optional, and its absence activates
`RULE_BASED_FALLBACK`.

## Pré-requisitos e diretórios

Na máquina de build:

- Git e um checkout no commit que será publicado;
- Docker com suporte a BuildKit/Buildx e `linux/amd64`;
- espaço para a imagem e seu arquivo TAR;
- acesso SSH autorizado à instância.

Na OCI, confirme primeiro:

```bash
cat /etc/os-release
uname -m
dpkg --print-architecture
docker version
docker compose version
```

Os resultados de arquitetura devem ser `x86_64` e `amd64`. Use os diretórios:

```text
/opt/energiai/repository              checkout identificável do repositório
/opt/energiai/config/backend.env      ambiente real, fora do Git
/opt/energiai/images                  arquivos de imagem transferidos
```

Não clone o repositório sobre `/opt/energiai/config`, `/opt/energiai/logs` ou `/opt/energiai/data`. Por exemplo:

```bash
cd /opt/energiai
git clone https://github.com/No-Country-simulation/g9-br-team-09.git repository
cd /opt/energiai/repository
git fetch origin
git checkout --detach <COMMIT_VALIDADO>
```

Crie o diretório de imagens sem alterar recursivamente os diretórios preparados pela Issue #106:

```bash
sudo install -d -o ubuntu -g "$(id -gn ubuntu)" -m 0750 /opt/energiai/images
```

## Arquivo de ambiente real

O Compose lê `/opt/energiai/config/backend.env`. Crie o arquivo somente se ele estiver ausente; uma segunda execução não pode truncar um ambiente já preenchido. Recuse links e tipos inesperados, corrija apenas os metadados de um arquivo regular existente e não copie um arquivo preenchido para dentro do repositório:

```bash
env_path=/opt/energiai/config/backend.env
admin_group="$(id -gn ubuntu)"

if [[ -L "${env_path}" || ( -e "${env_path}" && ! -f "${env_path}" ) ]]; then
  printf 'REPROVADO: caminho de ambiente inseguro: %s\n' "${env_path}" >&2
  exit 1
fi
if [[ ! -e "${env_path}" ]]; then
  sudo install -o ubuntu -g "${admin_group}" -m 0600 /dev/null "${env_path}"
else
  sudo chown ubuntu:"${admin_group}" "${env_path}"
  sudo chmod 0600 "${env_path}"
fi
sudoedit /opt/energiai/config/backend.env
sudo stat --format='owner=%U group=%G mode=%a' /opt/energiai/config/backend.env
```

O resultado final deve indicar o proprietário administrativo, seu grupo primário e modo `600`. Nunca use `set -x`, `cat`, `less`, `head`, `tail` ou `docker compose config` sem `--quiet` sobre esse arquivo.

Use [`.env.example`](.env.example) apenas como referência de nomes e formatos. O arquivo real deve definir:

- `BACKEND_IMAGE` com uma tag imutável, nunca `latest` ou a tag móvel `develop`;
- `API_PUBLIC_HOSTNAME` with the public hostname only, without a scheme, port,
  path, or real IP stored in a versioned file;
- `SPRING_PROFILES_ACTIVE=oci`, coerente com o valor `oci` imposto pelo Compose;
- `JAVA_TOOL_OPTIONS` com limites adequados à VM;
- `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` recebidos por canal autorizado;
- parâmetros opcionais de conexão/pool do Oracle;
- URL e timeouts da API de ML;
- origens CORS exatas, separadas por vírgula e nunca `*`.

`DB_URL` deve ser a string JDBC Thin TLS sem wallet fornecida pelo OCI Console, no formato conceitual `jdbc:oracle:thin:@tcps://<host>:<porta>/<service-name>`. Não registre host, service name, usuário ou URL reais no Git, em tickets ou em comandos compartilhados. A conta da aplicação precisa apenas dos privilégios exigidos pelo schema; não conceda `DBA`.

Para validar intencionalmente o fallback sem implantar a FastAPI, use `ML_API_BASE_URL=http://127.0.0.1:9`. Esse endereço é o loopback do próprio container e deve permanecer indisponível. Os timeouts conservadores impedem espera prolongada.

O valor inicial recomendado da JVM é:

```text
-XX:MaxRAMPercentage=55.0 -XX:InitialRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError
```

Os percentuais e o limite do container devem ser confirmados sob carga na instância real.

## Public HTTPS with Caddy

### Zero-cost sslip.io hostname

The OCI instance uses an ephemeral public IP, so this project does not require
a purchased domain. Obtain the current address from the OCI Console or from the
Terraform output in an authorized environment, then derive a hostname using
sslip.io:

```text
<CURRENT_OCI_PUBLIC_IP>.sslip.io
```

The public hostname may be documented in the main project README and in
deployment evidence because it is intentionally internet-facing. Do not place
the live address in `.env.example`, the Caddyfile, Terraform source or reusable
configuration templates. Never publish private IPs, OCIDs, credentials or
database connection details.

Because the instance address is ephemeral, stopping or recreating the instance
may require updating `API_PUBLIC_HOSTNAME`, the documented public links and the
issued certificate.

In the external `/opt/energiai/config/backend.env`, set:

```text
API_PUBLIC_HOSTNAME=<CURRENT_OCI_PUBLIC_IP>.sslip.io
```

The [Caddyfile](Caddyfile) reads this value through Caddy environment
substitution and sends traffic to `backend:8080`. Caddy automatically manages
HTTP-to-HTTPS redirects and publicly trusted certificates when the hostname
resolves to the instance, TCP ports 80 and 443 are reachable, and the ACME
provider can complete validation.

### CORS and Vercel

After the final Vercel production URL is known, set its exact origin in the
external backend environment file. Use the scheme and hostname only, with no
path, and keep multiple exact origins comma-separated:

```text
CORS_ALLOWED_ORIGINS=https://<FINAL_VERCEL_HOSTNAME>
```

Never use `*` for the production browser origin. Restart only the backend after
changing this value, then validate the browser preflight against the deployed
HTTPS endpoint.

In the Vercel project settings, configure this production environment variable:

```text
VITE_API_BASE_URL=https://<API_PUBLIC_HOSTNAME>/api/v1
```

Redeploy the frontend after changing a Vite build-time variable. Do not commit
the live OCI hostname to the frontend source or its example environment file.

### Validate and start Caddy

Validate the Compose model silently with the external environment file, then
validate the Caddyfile using the exact pinned image:

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  config --quiet

O container executa sem privilégios para ignorar permissões do host.
O Caddyfile não contém credenciais e precisa ser legível dentro do container.

chmod 0644 infra/deploy/oci/Caddyfile
stat --format='owner=%U group=%G mode=%a' infra/deploy/oci/Caddyfile

docker run --rm \
  --env-file /opt/energiai/config/backend.env \
  --volume "${PWD}/infra/deploy/oci/Caddyfile:/etc/caddy/Caddyfile:ro" \
  caddy:2.11.4-alpine \
  caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile

docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  up -d --no-build caddy
```

The backend deploy helper still pulls, recreates, verifies, and rolls back only
the `backend` service. It does not replace Caddy state or remove its named
volumes. Start Caddy once after configuring the hostname; reload or recreate it
deliberately after a future Caddyfile or hostname change.

Before calling the endpoint public, verify in the deployed environment that
DNS resolution points to the current instance, HTTP redirects to HTTPS, the
certificate is trusted, ports 80/443 are reachable, port 8080 is not publicly
reachable, and health/readiness, CORS, and an energy-analysis flow work through
the HTTPS URL. Local Compose and Caddyfile validation do not prove any of those
runtime conditions.

## Build externo para linux/amd64

Na raiz do checkout local validado, identifique o commit e use-o na tag:

```bash
commit_completo="$(git rev-parse HEAD)"
image="docker.io/pxs00/energiai-backend:sha-${commit_completo}"

docker build \
  --platform linux/amd64 \
  -t "${image}" \
  backend/

docker image inspect \
  "${image}" \
  --format '{{.Os}}/{{.Architecture}}'

docker save \
  -o "energiai-backend-${commit_completo}.tar" \
  "${image}"
```

O `docker image inspect` deve retornar exatamente:

```text
linux/amd64
```

Não execute esse build na `VM.Standard.E2.1.Micro` como fluxo principal.

### Transferência e carregamento

Transfira sem registrar IP ou caminho de chave reais na documentação ou no shell history compartilhado:

```bash
scp \
  "energiai-backend-<commit-completo>.tar" \
  ubuntu@<IP_PUBLICO>:/opt/energiai/images/
```

Quando uma chave específica for necessária:

```bash
scp \
  -i <CAMINHO_DA_CHAVE_PRIVADA> \
  "energiai-backend-<commit-completo>.tar" \
  ubuntu@<IP_PUBLICO>:/opt/energiai/images/
```

Na OCI:

```bash
docker load -i /opt/energiai/images/energiai-backend-<commit-completo>.tar
docker image inspect \
  docker.io/pxs00/energiai-backend:sha-<commit-completo> \
  --format '{{.Os}}/{{.Architecture}}'
```

Selecione essa tag em `BACKEND_IMAGE` usando `sudoedit`. Não altere as credenciais durante uma troca de imagem.

### Imagem imutável publicada no Docker Hub

O repositório existente é `docker.io/pxs00/energiai-backend`. Esta automação não cria pacote algum no GitHub Container Registry.

Se o commit já tiver sido publicado pelo workflow existente, use somente a tag imutável:

```bash
docker pull docker.io/pxs00/energiai-backend:sha-<commit-completo>
docker image inspect \
  docker.io/pxs00/energiai-backend:sha-<commit-completo> \
  --format '{{.Os}}/{{.Architecture}}'
```

Defina `BACKEND_IMAGE=docker.io/pxs00/energiai-backend:sha-<commit-completo>`. A tag contém os 40 caracteres do commit; `latest`, `develop` e tags legadas de SHA curto não são aceitos pelo fluxo automatizado.

O workflow usa `DOCKERHUB_USERNAME` e `DOCKERHUB_TOKEN` para publicar. A OCI recebe somente o token de deploy, `DOCKERHUB_DEPLOY_TOKEN`, com permissão de leitura: o token de publicação nunca sai do runner e nenhum dos dois é armazenado em `backend.env`.

## Manual GitHub Actions deployment — Issues #109 and #139

The [backend OCI workflow](../../../.github/workflows/backend-oci-deploy.yml)
remains manual and is triggered only with `workflow_dispatch`. Pull requests and
pushes never deploy. Runs are serialized in the `backend-oci-deploy` concurrency
group without cancelling an active deployment.

Choose exactly one operation:

- `validate` accepts a branch, tag, or commit, resolves it to one immutable
  40-character SHA, checks out that SHA in detached HEAD state, and runs all
  validation without publishing or deploying. It does not require confirmation.
- `deploy-preview` is an integration deployment, not a production release. Its
  `ref` must be the full lowercase 40-character SHA of a commit reachable from
  the current `origin/develop`. Branch names, tags, abbreviated SHAs, uppercase
  values, and commits outside `develop` are rejected. It requires
  `confirmation=DEPLOY` and still uses the protected `oci-production`
  environment.
- `deploy` is the production operation. It requires literal `ref=main`, verifies
  that the resolved commit is exactly the current `origin/main` HEAD, and
  requires `confirmation=DEPLOY`. Preview support does not weaken this
  production guardrail.

For both deployment operations, the validation job's resolved SHA is the only
source used for the detached publish checkout, Docker tag
`docker.io/pxs00/energiai-backend:sha-<full-sha>`, OCI repository checkout,
digest verification, readiness checks, smoke tests, and rollback. The workflow
never deploys `develop`, `latest`, a branch name, or an abbreviated SHA.

The flow is:

1. authorize the selected operation, resolve and check out the immutable SHA,
   then run Maven verification, behavioral source-policy tests, Bash syntax,
   ShellCheck `v0.10.0`, actionlint `1.7.7`, fixtures, fictitious Compose
   validation, Caddyfile validation, whitespace checks, and an external
   `linux/amd64` Docker build without publication;
2. for `deploy-preview` or `deploy`, publish only the immutable full-SHA
   `linux/amd64` image, preserving its OCI labels and verified digest;
3. through the protected `oci-production` environment, transfer only the
   temporary helper over strict SSH, atomically update only `BACKEND_IMAGE`, and
   run `docker compose pull backend` plus `up -d --no-build backend`;
4. wait for local readiness and run
   `infra/tests/smoke/backend-oci-smoke.sh` against
   `http://127.0.0.1:8080/api/v1`;
5. after any post-update failure, restore the previous immutable image and the
   checkout matching that image, recreate the backend without a build, and wait
   for previous readiness. The workflow remains failed even after a successful
   rollback.

This backend workflow deploys neither the frontend nor Data Science/FastAPI.
A preview only permits integration validation of the selected backend commit;
it does not promote `develop` or represent a production release.

O helper recusa iniciar caso a imagem em execução, `BACKEND_IMAGE` e o checkout OCI não formem o mesmo estado Docker Hub `sha-<40-hex>`, o arquivo externo não seja regular com permissões restritivas ou o checkout tenha alterações locais. Isso evita substituir uma versão não identificável ou sobrescrever trabalho operacional; faça a migração inicial pelo procedimento manual antes de ativar a automação.

### Configuração obrigatória do GitHub Environment

Crie o Environment `oci-production`, aplique aprovadores/regras de proteção adequados e configure nele somente os secrets de OCI abaixo.

- `OCI_COMPUTE_HOST`, `OCI_COMPUTE_USER` e `OCI_COMPUTE_SSH_PORT` (normalmente `22`);
- `OCI_COMPUTE_SSH_PRIVATE_KEY`, a chave privada exclusiva para o deploy;
- `OCI_COMPUTE_KNOWN_HOSTS`, a entrada completa e previamente verificada do host SSH, incluindo host e porta quando necessário;

Os três secrets Docker Hub abaixo já existem no repositório e não exigem acesso ao Environment para publicação:

- `DOCKERHUB_USERNAME`, usado tanto no login de publicação quanto no login da OCI;
- `DOCKERHUB_TOKEN`, usado somente para publicar;
- `DOCKERHUB_DEPLOY_TOKEN`, token separado somente de leitura para pull na OCI.

O job de publicação não declara `oci-production`; ele usa os repository secrets `DOCKERHUB_USERNAME` e `DOCKERHUB_TOKEN` em `docker/login-action`. Somente o job de deploy declara `oci-production` e recebe `DOCKERHUB_DEPLOY_TOKEN`, transmitindo-o temporariamente para `docker login docker.io --password-stdin` na VM. Nunca envie `DOCKERHUB_TOKEN` para a OCI. O helper cria um `DOCKER_CONFIG` temporário com permissões `0700`, usado pelo login, pull, Compose e rollback; ao encerrar, faz logout silencioso, remove esse diretório e o arquivo de autenticação transferido, e desfaz `DOCKER_CONFIG`.

Obtenha a impressão digital SSH por um canal confiável durante o provisionamento e armazene a linha resultante em `OCI_COMPUTE_KNOWN_HOSTS`. Não aceite a primeira conexão automaticamente e não use `StrictHostKeyChecking=no`. A workflow usa `BatchMode`, `IdentitiesOnly`, `StrictHostKeyChecking=yes`, `UserKnownHostsFile` controlado e timeout de conexão; cria chave, `known_hosts`, diretório remoto e as credenciais temporárias com permissões restritas e os remove ao fim. O token de deploy é enviado por canal SSH somente para `docker login --password-stdin`, não como argumento de linha de comando.

As credenciais Oracle continuam exclusivamente em `/opt/energiai/config/backend.env`. A workflow não copia, lista, imprime, renderiza com Compose sem `--quiet` nem usa esse arquivo como secret do GitHub. Nenhuma credencial Docker Hub pertence a `backend.env` ou ao repositório. Depois de cada pull da nova imagem, o helper confirma `linux/amd64` e que os `RepoDigests` locais incluem `pxs00/energiai-backend@<digest-publicado>` antes de executar `docker compose up`; o rollback verifica a arquitetura e continua usando somente a imagem anterior com SHA imutável quando o digest histórico não está disponível.

### Operation, summary, and diagnostics

Run `operation=validate` first. For an integration deployment, copy the full SHA
that belongs to `develop`, select `operation=deploy-preview`, put that SHA in
`ref`, and enter `confirmation=DEPLOY`. For production, select
`operation=deploy`, use literal `ref=main`, and enter the same confirmation. If
needed, select the expected classification source for the smoke test.

The Job Summary records the operation, requested ref, resolved full SHA, source
policy, immutable image and digest, platform, environment, validation result,
timestamps, readiness, smoke tests, rollback, and safe previous/new versions.
It does not contain the host, JDBC configuration, external environment, key, or
token.

Uma falha antes da troca de `BACKEND_IMAGE` não requer rollback. Em falhas de pull, Compose, readiness ou smoke após a troca, o helper tenta rollback. Se o rollback também falhar, a job permanece falha e o resumo indica `failed-rollback-failed`; investigue através do acesso SSH aprovado e das verificações seguras de logs abaixo. O rollback da aplicação não reverte migrations compatíveis de Oracle.

Para rotação ou revogação, revogue a credencial Docker Hub afetada, atualize somente o repository secret correspondente, execute `validate` e faça uma implantação aprovada. Nunca reutilize `DOCKERHUB_TOKEN` na VM, `DOCKERHUB_DEPLOY_TOKEN` no publish job, nem adicione credenciais a `backend.env` ou ao repositório.

## Validação do Compose

Na OCI, a partir de `/opt/energiai/repository`, valide sem resolver e imprimir o ambiente:

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  config --quiet
```

Para uma validação local sem o caminho absoluto da OCI, crie uma cópia temporária restrita e exclusivamente fictícia:

```bash
umask 077
env_ficticio="$(mktemp)"
trap 'rm -f -- "${env_ficticio}"' EXIT
cp infra/deploy/oci/.env.example "${env_ficticio}"
sed -i \
  -e 's|^BACKEND_IMAGE=.*|BACKEND_IMAGE=docker.io/pxs00/energiai-backend:sha-0000000000000000000000000000000000000000|' \
  -e "s|^BACKEND_ENV_FILE=.*|BACKEND_ENV_FILE=${env_ficticio}|" \
  "${env_ficticio}"

docker compose \
  --env-file "${env_ficticio}" \
  -f infra/deploy/oci/compose.yaml \
  config --quiet
```

Essa validação não inicia containers e não deve usar valores reais.

## Inicialização e estado do serviço

Na OCI, sempre execute o Compose a partir do checkout validado e com o arquivo externo explícito:

```bash
cd /opt/energiai/repository

docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  up -d --no-build

docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  ps
```

Before using the documented log command, run the silent security check. Do not
use `up --build` on OCI. Compose publishes backend port `8080` only on
`127.0.0.1`; Caddy is the sole public entry point on TCP ports `80` and `443`.
No Oracle, FastAPI, Actuator-specific, Docker API, or other application port is
published. The `energiai-oci` network is a bridge and does not use
`network_mode: host`.

O serviço usa `restart: unless-stopped`, `init: true`, `no-new-privileges`, remove todas as capabilities Linux, aguarda até 30 segundos ao parar e limita o container a `640m`, `0.75` CPU e 128 processos. Não há mount de Docker socket ou de diretórios sensíveis. A rotação `json-file` (`10m`, três arquivos) vem do daemon configurado pela Issue #106 e não é duplicada neste Compose.

Caddy has a separate conservative ceiling of `96m`, `0.20` CPU, and 64
processes. It drops all Linux capabilities and adds back only
`NET_BIND_SERVICE` for ports 80 and 443. Together, the declared service limits
leave capacity for Ubuntu, Docker, SSH, and short operational tasks on the
1 GB VM; confirm the limits under real traffic before increasing them.

## Health, liveness, readiness e túnel SSH

No próprio host, valide os três endpoints sem exibir detalhes internos:

```bash
for endpoint in health health/liveness health/readiness; do
  if curl --fail --silent --show-error \
      "http://127.0.0.1:8080/api/v1/actuator/${endpoint}" \
      | jq -e '.status == "UP"' >/dev/null; then
    printf 'APROVADO: %s esta UP\n' "${endpoint}"
  else
    printf 'REPROVADO: %s nao esta UP\n' "${endpoint}" >&2
    exit 1
  fi
done
```

Para acesso remoto, mantenha a sessão SSH aberta:

```bash
ssh \
  -i <CAMINHO_DA_CHAVE_PRIVADA> \
  -L 8080:127.0.0.1:8080 \
  ubuntu@<IP_PUBLICO>
```

Em outro terminal local:

```bash
curl --fail http://localhost:8080/api/v1/actuator/health
curl --fail http://localhost:8080/api/v1/actuator/health/liveness
curl --fail http://localhost:8080/api/v1/actuator/health/readiness
```

Todos devem retornar `status: UP`. Não abra 8080 na VCN, NSG, security lists ou firewall para executar essa validação.

## Análise energética, fallback e persistência

Com `ML_API_BASE_URL=http://127.0.0.1:9`, execute no host OCI ou através do túnel. O contrato atual retorna o identificador no campo `id`:

```bash
umask 077
resposta="$(mktemp)"
detalhe="$(mktemp)"
trap 'rm -f -- "${resposta}" "${detalhe}"' EXIT

curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"consumo_kwh":420,"uso_horario_pico":true,"quantidade_equipamentos":10,"tipo_imovel":"CASA","horas_alto_consumo":8}' \
  --output "${resposta}" \
  http://127.0.0.1:8080/api/v1/analise-energetica

analysis_id="$(jq -er '.id | select(type == "number")' "${resposta}")"
jq -e '.fonte_classificacao == "RULE_BASED_FALLBACK"' "${resposta}" >/dev/null
printf 'APROVADO: analise %s criada com RULE_BASED_FALLBACK\n' "${analysis_id}"

curl --fail --silent --show-error \
  --output "${detalhe}" \
  "http://127.0.0.1:8080/api/v1/analise-energetica/${analysis_id}"
jq -e --argjson expected_id "${analysis_id}" \
  '.id == $expected_id and .fonte_classificacao == "RULE_BASED_FALLBACK"' \
  "${detalhe}" >/dev/null
```

Confirme que a FastAPI indisponível não afeta readiness:

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:8080/api/v1/actuator/health/readiness \
  | jq -e '.status == "UP"' >/dev/null
```

Reinicie apenas o backend e consulte o mesmo registro:

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  restart backend

curl --fail --silent --show-error \
  --retry 20 \
  --retry-delay 3 \
  --retry-connrefused \
  --output "${detalhe}" \
  "http://127.0.0.1:8080/api/v1/analise-energetica/${analysis_id}"
jq -e --argjson expected_id "${analysis_id}" \
  '.id == $expected_id and .fonte_classificacao == "RULE_BASED_FALLBACK"' \
  "${detalhe}" >/dev/null
printf 'APROVADO: analise %s permaneceu no Oracle apos o restart\n' "${analysis_id}"
```

O registro está no Oracle, não no filesystem do container. Não são necessários volumes de dados locais para esse fluxo.

## Oracle, HikariCP e Flyway

O profile `oci` mantém:

```text
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.clean-disabled=true
spring.flyway.validate-on-migrate=true
```

Nunca execute `flyway clean`, não altere `ddl-auto` para `update` e não aumente privilégios para `DBA`. Um startup completo, health/readiness `UP`, criação da análise e leitura após restart comprovam conjuntamente que:

- o HikariCP iniciou e obteve conexão;
- o Oracle está acessível e é obrigatório para readiness;
- Flyway validou ou aplicou a migration sem impedir o startup;
- Hibernate validou o schema;
- a tabela de análises aceita escrita e leitura persistente.

Os testes automatizados também verificam que o grupo readiness contém `db` e não contém um indicador da FastAPI. A comprovação final de TLS, schema e migration deve ser feita no Oracle real durante a janela autorizada, sem imprimir a URL JDBC.

## CPU, memória e disco

Após estabilizar o serviço:

```bash
docker stats --no-stream energiai-backend
docker system df
df -h / /var/lib/docker /opt/energiai
```

Confirme ausência de OOM/restarts e folga para Ubuntu, daemon Docker e SSH. Se `640m`, `0.75` CPU ou os percentuais da JVM forem alterados após medição, registre a justificativa e valide novamente health, análise e restart. Não execute a FastAPI ou outro serviço pesado nessa VM sem uma avaliação posterior.

## Inspeção segura de logs

Antes de exibir logs, faça uma checagem silenciosa. Ela grava uma cópia temporária com modo restrito, compara sem imprimir os três valores reais, procura nomes sensíveis e stack traces, e informa apenas aprovação ou reprovação:

```bash
set +x
set -Eeuo pipefail
umask 077
log_file="$(mktemp)"
trap 'rm -f -- "${log_file}"' EXIT

docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  logs --no-color backend >"${log_file}"

log_check=0
for key in DB_URL DB_USERNAME DB_PASSWORD; do
  if ! value="$(sudo awk -v prefix="${key}=" '
      index($0, prefix) == 1 { print substr($0, length(prefix) + 1); found = 1; exit }
      END { if (!found) exit 1 }
    ' /opt/energiai/config/backend.env)"; then
    printf 'REPROVADO: variavel obrigatoria %s ausente\n' "${key}" >&2
    log_check=1
  elif [[ -z "${value}" ]]; then
    printf 'REPROVADO: variavel obrigatoria %s vazia\n' "${key}" >&2
    log_check=1
  elif grep -Fq -- "${value}" "${log_file}"; then
    printf 'REPROVADO: valor de %s encontrado nos logs\n' "${key}" >&2
    log_check=1
  else
    printf 'APROVADO: valor de %s ausente dos logs\n' "${key}"
  fi
  unset value
done

if grep -Eq '(^|[^[:alnum:]_])(DB_URL|DB_USERNAME|DB_PASSWORD)([^[:alnum:]_]|$)' "${log_file}"; then
  printf 'REPROVADO: nome de variavel sensivel encontrado nos logs\n' >&2
  log_check=1
else
  printf 'APROVADO: nomes de variaveis sensiveis ausentes dos logs\n'
fi

if grep -Eq '(^|[[:space:]])at [[:alnum:]_.$]+\([^)]*:[0-9]+\)|Exception:|Caused by:' "${log_file}"; then
  printf 'REPROVADO: stack trace inesperado encontrado nos logs\n' >&2
  log_check=1
else
  printf 'APROVADO: nenhum stack trace inesperado encontrado\n'
fi

exit "${log_check}"
```

Só depois de uma checagem aprovada use, quando necessário:

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  logs --tail=100 backend
```

Não use `docker inspect` no container como diagnóstico comum: o ambiente do processo contém credenciais. A verificação acima cobre os logs existentes, mas deve ser repetida após falhas ou atualizações.

## Atualização

1. Valide outro commit e construa externamente uma nova tag imutável `linux/amd64`.
2. Transfira/carregue a imagem ou faça pull da tag Docker Hub `sha-<commit-completo>`.
3. Confirme a arquitetura com `docker image inspect`.
4. Preserve `/opt/energiai/config/backend.env` e altere apenas `BACKEND_IMAGE` com `sudoedit`.
5. Valide e recrie sem build:

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  config --quiet

docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  up -d --no-build
```

Repita health, readiness, análise, persistência, logs e recursos. Não exclua a imagem anterior antes de encerrar a janela de rollback.

## Rollback

1. Registre a imagem atual com `docker compose ... images backend` e recupere a tag imutável anterior do histórico da implantação.
2. Não remova nem substitua `/opt/energiai/config/backend.env`.
3. Garanta que a imagem anterior existe com `docker image inspect <IMAGEM_IMUTAVEL_ANTERIOR>` ou carregue seu TAR.
4. Altere somente `BACKEND_IMAGE` para a tag anterior usando `sudoedit`.
5. Valide e recrie:

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  config --quiet

docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  up -d --no-build
```

6. Valide health, readiness e consulte IDs já persistidos.

Não execute migrations destrutivas, `flyway clean`, remoção de volumes ou exclusão de dados Oracle. O rollback da imagem não reverte automaticamente mudanças compatíveis de schema; revise a compatibilidade antes de voltar.

Para identificar a imagem do serviço sem inspecionar o ambiente do container:

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  images backend
```

## Parada e limpeza controlada

Para parar o serviço preservando imagens, ambiente e dados Oracle:

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  down
```

Depois da janela de rollback, remova manualmente apenas um TAR identificado em `/opt/energiai/images` ou uma imagem antiga sem containers dependentes. Revise o alvo antes de `rm` ou `docker image rm`. Nunca remova `/opt/energiai/config/backend.env`, `/var/lib/docker`, `/var/lib/containerd`, volumes de forma ampla ou dados do Oracle como parte da limpeza comum.

## Troubleshooting

- **`BACKEND_IMAGE` ausente:** preencha uma tag imutável; o Compose falha deliberadamente quando ela está vazia.
- **Imagem com arquitetura errada:** refaça o build com `--platform linux/amd64`; não adicione ARM64.
- **Container encerra por memória:** consulte `docker stats`, estado/restarts e logs após a checagem segura; ajuste JVM/limite apenas com medição.
- **Profile ou datasource incorreto:** confirme apenas a presença das variáveis obrigatórias e `SPRING_PROFILES_ACTIVE=oci`, sem imprimi-las.
- **Readiness indisponível:** Oracle é obrigatório. Verifique conectividade TLS, ACL, `CREATE SESSION`, pool e schema; não conceda `DBA`.
- **Flyway/Hibernate falha:** revise compatibilidade do schema e `flyway_schema_history`; não use `flyway clean` ou `ddl-auto=update`.
- **Fallback não aparece:** confirme que o endereço de teste é o loopback indisponível do container e que os timeouts expiraram; não implante FastAPI nesta issue.
- **Túnel falha:** confirme SSH e o bind local do container. Não abra 8080 publicamente.
- **CORS nega o frontend:** configure origens exatas separadas por vírgula; não use `*`.

## Out of scope and pending deployed-environment validation

This change does not install Docker, reserve an OCI public IP, purchase a
domain, create OCI DNS records or a Load Balancer, deploy FastAPI or the
frontend, modify the Oracle wallet/schema/migrations or classification rules,
add external observability, or deploy automatically from a pull request or
push.

Image build/load, Compose execution, Caddy startup, public DNS resolution,
certificate issuance and browser trust, HTTP-to-HTTPS redirect, public listener
reachability, TCP/8080 isolation, browser CORS, Vercel integration, Oracle TLS,
Flyway, live health/readiness, fallback behavior, persistence after restart,
and resource-limit measurements still require an authorized Ubuntu 24.04
`x86_64/amd64` OCI environment. This runbook contains no credentials and does
not itself authorize OCI access.
