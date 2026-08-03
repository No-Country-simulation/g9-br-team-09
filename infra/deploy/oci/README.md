# Implantação do backend na OCI — Issues #107, #109 e #110

Este runbook abrange a implantação manual, reproduzível e reversível do Spring
Boot do EnergiAI, a automação acionada manualmente da Issue #109 e o proxy
reverso HTTPS público da Issue #110. O Terraform provisiona a rede da OCI
separadamente. Esta implantação não instala Docker, não implanta a FastAPI nem
expõe a porta `8080` do backend à internet.

## Escopo, dependências e arquitetura

O fluxo depende das entregas anteriores:

- Issue #104: instância `VM.Standard.E2.1.Micro`, Canonical Ubuntu 24.04 LTS, `x86_64/amd64`, rede e acesso SSH;
- Issue #105: health geral, liveness de processo e readiness com Oracle obrigatório;
- Issue #106: Docker Engine, Compose, Buildx, rotação de logs e diretórios `/opt/energiai`.
- Issue #108: smoke tests de contrato reutilizados após cada atualização.

A imagem é construída fora da VM para `linux/amd64`, identificada por uma tag imutável completa e publicada no repositório Docker Hub existente `docker.io/pxs00/energiai-backend`. A alternativa manual de transferir um TAR permanece disponível para recuperação controlada. Na OCI o Compose apenas inicia a imagem com `--no-build`; a `VM.Standard.E2.1.Micro` não faz build de imagens.

O Compose executa dois serviços na rede bridge `energiai-oci` existente:

- `backend` mantém o bind do host `127.0.0.1:8080:8080`, para que os smoke
  tests locais e o túnel SSH continuem funcionando sem tornar a porta `8080`
  pública;
- `caddy` usa a imagem fixada `caddy:2.11.4-alpine`, publica somente as portas
  TCP `80` e `443` e faz proxy para `backend:8080` pela rede Docker.

O Caddy armazena o certificado e o estado de execução nos volumes nomeados
`caddy_data` e `caddy_config`. O Oracle Autonomous Database continua
obrigatório para a readiness do backend. A FastAPI continua opcional, e sua
ausência ativa
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
- `API_PUBLIC_HOSTNAME` somente com o hostname público, sem esquema, porta,
  caminho ou IP real em arquivo versionado;
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

## HTTPS público com Caddy

### Hostname sslip.io sem custo

A instância da OCI usa um IP público efêmero; portanto, este projeto não exige
um domínio adquirido. Obtenha o endereço atual no Console da OCI ou na saída do
Terraform em ambiente autorizado e derive um hostname usando sslip.io:

```text
<CURRENT_OCI_PUBLIC_IP>.sslip.io
```

O hostname público pode ser documentado no README principal do projeto e nas
evidências de implantação, pois ele é intencionalmente exposto à internet. Não
coloque o endereço ativo em `.env.example`, no Caddyfile, no código do
Terraform ou em modelos de configuração reutilizáveis. Nunca publique IPs
privados, OCIDs, credenciais ou detalhes de conexão do banco de dados.

Como o endereço da instância é efêmero, parar ou recriar a instância pode exigir
a atualização de `API_PUBLIC_HOSTNAME`, dos links públicos documentados e do
certificado emitido.

No arquivo externo `/opt/energiai/config/backend.env`, defina:

```text
API_PUBLIC_HOSTNAME=<CURRENT_OCI_PUBLIC_IP>.sslip.io
```

O [Caddyfile](Caddyfile) lê esse valor pela substituição de ambiente do Caddy e
envia o tráfego para `backend:8080`. O Caddy gerencia automaticamente os
redirecionamentos de HTTP para HTTPS e certificados publicamente confiáveis
quando o hostname resolve para a instância, as portas TCP 80 e 443 estão
acessíveis e o provedor ACME consegue concluir a validação.

### CORS e Vercel

Depois de conhecer a URL final de produção da Vercel, defina sua origem exata
no arquivo de ambiente externo do backend. Use somente o esquema e o hostname,
sem caminho, e mantenha múltiplas origens exatas separadas por vírgula:

```text
CORS_ALLOWED_ORIGINS=https://<FINAL_VERCEL_HOSTNAME>
```

Nunca use `*` para a origem do navegador em produção. Reinicie somente o
backend depois de alterar esse valor e valide o preflight do navegador no
endpoint HTTPS implantado.

Nas configurações do projeto Vercel, configure esta variável de ambiente de
produção:

```text
VITE_API_BASE_URL=https://<API_PUBLIC_HOSTNAME>/api/v1
```

Reimplante o frontend depois de alterar uma variável de build do Vite. Não faça
commit do hostname ativo da OCI no código do frontend nem em seu arquivo de
ambiente de exemplo.

### Validar e iniciar o Caddy

Valide silenciosamente o modelo do Compose com o arquivo de ambiente externo e,
em seguida, valide o Caddyfile usando a imagem exata fixada:

O container executa sem privilégios para ignorar permissões do host. O
Caddyfile não contém credenciais e precisa ser legível dentro do container.

```bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  config --quiet
```

```bash
chmod 0644 infra/deploy/oci/Caddyfile
stat --format='owner=%U group=%G mode=%a' infra/deploy/oci/Caddyfile
```

```bash
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

O helper de deploy do backend continua fazendo pull, recriando, verificando e
executando rollback somente do serviço `backend`. Ele não substitui o estado do
Caddy nem remove seus volumes nomeados. Inicie o Caddy uma vez após configurar
o hostname; recarregue-o ou recrie-o deliberadamente após uma futura alteração
do Caddyfile ou do hostname.

Antes de considerar o endpoint público, verifique no ambiente implantado que a
resolução DNS aponta para a instância atual, HTTP redireciona para HTTPS, o
certificado é confiável, as portas 80/443 estão acessíveis, a porta 8080 não
está publicamente acessível e health/readiness, CORS e um fluxo de análise
energética funcionam pela URL HTTPS. A validação local do Compose e do
Caddyfile não comprova nenhuma dessas condições de execução.

## Construção externa para linux/amd64

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

## Implantação manual via GitHub Actions — Issues #109 e #139

A [workflow do backend na OCI](../../../.github/workflows/backend-oci-deploy.yml)
permanece manual e é acionada somente por `workflow_dispatch`. Pull requests e
pushes nunca implantam. As execuções são serializadas no grupo de concorrência
`backend-oci-deploy`, sem cancelar uma implantação ativa.

Escolha exatamente uma operação:

- `validate` aceita uma branch, tag ou commit, resolve-o para um SHA imutável de
  40 caracteres, faz checkout desse SHA em estado HEAD destacado e executa toda
  a validação sem publicar nem implantar. Não exige confirmação.
- `deploy-preview` é uma implantação de integração, não uma release de
  produção. Seu `ref` deve ser o SHA completo em minúsculas, com 40 caracteres,
  de um commit alcançável a partir do `origin/develop` atual. Nomes de branches,
  tags, SHAs abreviados, valores em maiúsculas e commits fora de `develop` são
  rejeitados. Exige `confirmation=DEPLOY` e continua usando o ambiente
  protegido `oci-production`.
- `deploy` é a operação de produção. Exige o literal `ref=main`, verifica se o
  commit resolvido é exatamente o HEAD atual de `origin/main` e exige
  `confirmation=DEPLOY`. O suporte a preview não enfraquece essa proteção de
  produção.

Para ambas as operações de implantação, o SHA resolvido pelo job de validação é
a única fonte usada para o checkout destacado de publicação, a tag Docker
`docker.io/pxs00/energiai-backend:sha-<full-sha>`, o checkout do repositório na
OCI, a verificação de digest, as checagens de readiness, os smoke tests e o
rollback. A workflow nunca implanta `develop`, `latest`, um nome de branch ou
um SHA abreviado.

O fluxo é:

1. autorize a operação selecionada, resolva e faça checkout do SHA imutável e,
   então, execute a verificação Maven, testes de política de código
   comportamental, sintaxe Bash, ShellCheck `v0.10.0`, actionlint `1.7.7`,
   fixtures, validação fictícia do Compose, validação do Caddyfile, checagens de
   whitespace e um build Docker externo `linux/amd64` sem publicação;
2. para `deploy-preview` ou `deploy`, publique somente a imagem imutável
   `linux/amd64` com SHA completo, preservando seus labels OCI e o digest
   verificado;
3. pelo ambiente protegido `oci-production`, transfira somente o helper
   temporário por SSH estrito, atualize atomicamente apenas `BACKEND_IMAGE` e
   execute `docker compose pull backend` junto de `up -d --no-build backend`;
4. aguarde a readiness local e execute
   `infra/tests/smoke/backend-oci-smoke.sh` contra
   `http://127.0.0.1:8080/api/v1`;
5. após qualquer falha pós-atualização, restaure a imagem imutável anterior e
   o checkout correspondente a ela, recrie o backend sem build e aguarde a
   readiness anterior. A workflow permanece falha mesmo após um rollback bem-
   sucedido.

Essa workflow de backend não implanta nem o frontend nem Data Science/FastAPI.
Um preview permite somente a validação de integração do commit de backend
selecionado; ele não promove `develop` nem representa uma release de produção.

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

### Operação, resumo e diagnósticos

Execute `operation=validate` primeiro. Para uma implantação de integração,
copie o SHA completo que pertence a `develop`, selecione
`operation=deploy-preview`, informe esse SHA em `ref` e insira
`confirmation=DEPLOY`. Para produção, selecione `operation=deploy`, use o
literal `ref=main` e insira a mesma confirmação. Se necessário, selecione a
fonte de classificação esperada para o smoke test.

O Job Summary registra a operação, o ref solicitado, o SHA completo resolvido,
a política de código, a imagem imutável e o digest, a plataforma, o ambiente,
o resultado da validação, timestamps, readiness, smoke tests, rollback e as
versões anterior/nova seguras. Ele não contém o host, a configuração JDBC, o
ambiente externo, a chave ou o token.

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

Antes de usar o comando de logs documentado, execute a checagem silenciosa de
segurança. Não use `up --build` na OCI. O Compose publica a porta `8080` do
backend somente em `127.0.0.1`; o Caddy é o único ponto de entrada público nas
portas TCP `80` e `443`. Nenhuma porta do Oracle, FastAPI, específica do
Actuator, da API Docker ou de outra aplicação é publicada. A rede
`energiai-oci` é uma bridge e não usa `network_mode: host`.

O serviço usa `restart: unless-stopped`, `init: true`, `no-new-privileges`, remove todas as capabilities Linux, aguarda até 30 segundos ao parar e limita o container a `640m`, `0.75` CPU e 128 processos. Não há mount de Docker socket ou de diretórios sensíveis. A rotação `json-file` (`10m`, três arquivos) vem do daemon configurado pela Issue #106 e não é duplicada neste Compose.

O Caddy tem um limite conservador separado de `96m`, `0.20` CPU e 64 processos.
Ele remove todas as capabilities Linux e adiciona de volta somente
`NET_BIND_SERVICE` para as portas 80 e 443. Juntos, os limites declarados dos
serviços deixam capacidade para Ubuntu, Docker, SSH e tarefas operacionais
curtas na VM de 1 GB; confirme os limites sob tráfego real antes de aumentá-
los.

## Verificações de saúde, liveness, readiness e túnel SSH

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

## Reversão (rollback)

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

## Solução de problemas

- **`BACKEND_IMAGE` ausente:** preencha uma tag imutável; o Compose falha deliberadamente quando ela está vazia.
- **Imagem com arquitetura errada:** refaça o build com `--platform linux/amd64`; não adicione ARM64.
- **Container encerra por memória:** consulte `docker stats`, estado/restarts e logs após a checagem segura; ajuste JVM/limite apenas com medição.
- **Profile ou datasource incorreto:** confirme apenas a presença das variáveis obrigatórias e `SPRING_PROFILES_ACTIVE=oci`, sem imprimi-las.
- **Readiness indisponível:** Oracle é obrigatório. Verifique conectividade TLS, ACL, `CREATE SESSION`, pool e schema; não conceda `DBA`.
- **Flyway/Hibernate falha:** revise compatibilidade do schema e `flyway_schema_history`; não use `flyway clean` ou `ddl-auto=update`.
- **Fallback não aparece:** confirme que o endereço de teste é o loopback indisponível do container e que os timeouts expiraram; não implante FastAPI nesta issue.
- **Túnel falha:** confirme SSH e o bind local do container. Não abra 8080 publicamente.
- **CORS nega o frontend:** configure origens exatas separadas por vírgula; não use `*`.

## Fora de escopo e validação pendente no ambiente implantado

Esta alteração não instala Docker, não reserva um IP público da OCI, não adquire
um domínio, não cria registros DNS da OCI ou um Load Balancer, não implanta a
FastAPI ou o frontend, não modifica a wallet/o schema/as migrations do Oracle
ou as regras de classificação, não adiciona observabilidade externa nem
implanta automaticamente a partir de uma pull request ou push.

O build/load da imagem, a execução do Compose, a inicialização do Caddy, a
resolução DNS pública, a emissão do certificado e a confiança do navegador, o
redirecionamento de HTTP para HTTPS, a acessibilidade do listener público, o
isolamento TCP/8080, o CORS do navegador, a integração com a Vercel, o TLS do
Oracle, o Flyway, health/readiness em execução, o comportamento de fallback, a
persistência após restart e as medições dos limites de recursos ainda exigem um
ambiente OCI autorizado com Ubuntu 24.04 `x86_64/amd64`. Este runbook não contém
credenciais e não autoriza, por si só, o acesso à OCI.
