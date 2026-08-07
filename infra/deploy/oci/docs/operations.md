# Operação e diagnóstico

Execute comandos no checkout /opt/energiai/repository e nunca exponha o conteúdo de backend.env.

## Compose, containers e saúde

Valide modelo e estado:

~~~bash
docker compose --env-file /opt/energiai/config/backend.env -f infra/deploy/oci/compose.yaml config --quiet
docker compose --env-file /opt/energiai/config/backend.env -f infra/deploy/oci/compose.yaml ps
~~~

backend tem bind loopback 127.0.0.1:8080, limite 640m, 0.75 CPU e 128 processos; caddy publica 80/443, limite 96m, 0.20 CPU e 64 processos. Ambos usam restart unless-stopped, init, capabilities restritas e no-new-privileges. A bridge não usa host networking; Caddy é a única entrada pública. Não use up --build.

No host, saúde, liveness e readiness devem retornar UP:

~~~bash
for endpoint in health health/liveness health/readiness; do
  curl --fail --silent --show-error "http://127.0.0.1:8080/api/v1/actuator/${endpoint}" | jq -e '.status == "UP"' >/dev/null
done
~~~

Para acesso remoto, mantenha ssh -i <CAMINHO_DA_CHAVE_PRIVADA> -L 8080:127.0.0.1:8080 ubuntu@<IP_PUBLICO> e use os mesmos endpoints em http://localhost:8080. Não abra 8080 em VCN, NSG, security list ou firewall.

## Análise, ML e persistência

No cenário de fallback configurado com ML_API_BASE_URL=http://127.0.0.1:9, o procedimento autenticado está na seção posterior deste documento.

O profile oci mantém spring.jpa.hibernate.ddl-auto=validate, spring.flyway.clean-disabled=true e spring.flyway.validate-on-migrate=true. Startup, readiness, escrita e leitura após restart demonstram HikariCP, Oracle obrigatório, Flyway e Hibernate, mas TLS/schema/migration devem ser comprovados no Oracle real sem imprimir JDBC. Nunca use flyway clean, ddl-auto=update ou DBA.

## Logs, recursos e troubleshooting

Antes de exibir logs, confirme que não há valores ou nomes DB_URL, DB_USERNAME, DB_PASSWORD nem stack traces inesperados; somente então use:

~~~bash
docker compose --env-file /opt/energiai/config/backend.env -f infra/deploy/oci/compose.yaml logs --tail=100 backend
docker stats --no-stream energiai-backend
docker system df
df -h / /var/lib/docker /opt/energiai
~~~

Não use docker inspect como diagnóstico comum: ambiente contém credenciais. Confirme ausência de OOM/restarts e folga para Ubuntu, Docker e SSH antes de alterar JVM, memória ou CPU.

- BACKEND_IMAGE ausente: informe tag imutável.
- Arquitetura errada: reconstrua com --platform linux/amd64.
- Readiness falha: Oracle é obrigatório; verifique TLS, ACL, CREATE SESSION, pool e schema sem conceder DBA.
- Fallback ausente: confirme loopback indisponível e timeout; não implante FastAPI como correção.
- CORS nega frontend: configure origens exatas separadas por vírgula, nunca *.
- Refresh/logout 403: confirme credenciais e X-XSRF-TOKEN; 401 trata sessão como não renovável, sem expor cookies ou tokens.
- Túnel falha: confirme SSH e bind loopback; não publique 8080.

Após cada deploy, aguarde readiness, rode smoke/health, valide análise e persistência, revise logs de forma segura e recursos. A validação local de Compose/Caddyfile não prova DNS, TLS, ACME, portas públicas, Oracle ou navegador.

## Validação do Compose e do Caddy

### Na OCI

A partir de /opt/energiai/repository, valide o modelo sem renderizar o ambiente:

~~~bash
docker compose \
  --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml \
  config --quiet
~~~

### Localmente, com valores exclusivamente fictícios

Esta verificação não inicia containers e não comprova OCI, Oracle, DNS, ACME, TLS público ou navegador:

~~~bash
umask 077
env_ficticio="$(mktemp)"
trap 'rm -f -- "${env_ficticio}"' EXIT
cp infra/deploy/oci/.env.example "${env_ficticio}"
sed -i \
  -e 's|^BACKEND_IMAGE=.*|BACKEND_IMAGE=docker.io/pxs00/energiai-backend:sha-0000000000000000000000000000000000000000|' \
  -e "s|^BACKEND_ENV_FILE=.*|BACKEND_ENV_FILE=${env_ficticio}|" \
  "${env_ficticio}"
docker compose --env-file "${env_ficticio}" \
  -f infra/deploy/oci/compose.yaml config --quiet
~~~

O Caddyfile não contém credenciais, deve ser legível pelo container sem privilégios e é validado com a imagem fixada do Compose:

~~~bash
chmod 0644 infra/deploy/oci/Caddyfile
stat --format='owner=%U group=%G mode=%a' infra/deploy/oci/Caddyfile
docker run --rm \
  --env-file /opt/energiai/config/backend.env \
  --volume "${PWD}/infra/deploy/oci/Caddyfile:/etc/caddy/Caddyfile:ro" \
  caddy:2.11.4-alpine \
  caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml up -d --no-build caddy
~~~

O helper automatizado atualiza e reverte somente backend; não substitui Caddy nem remove seus volumes. Inicie Caddy após configurar hostname e recrie-o deliberadamente apenas após alteração de hostname ou Caddyfile.

## Análise autenticada e persistência

No estado atual, POST e GET em /analise-energetica/** exigem Authorization: Bearer. Não use o fluxo anônimo que existia antes da autenticação. O procedimento reutilizável e autenticado é o [smoke test OCI](../../../tests/smoke/README.md): ele faz login com usuário técnico, valida /auth/me, cria análise, busca histórico e detalhe autenticados, confirma incremento e valida payload inválido, ID inexistente, readiness e fonte de classificação.

Para a janela inicial de fallback, configure ML_API_BASE_URL=http://127.0.0.1:9 sem imprimir backend.env, execute o smoke com EXPECTED_CLASSIFICATION_SOURCE=RULE_BASED_FALLBACK e restaure a configuração ao término. O smoke recebe credenciais por SMOKE_AUTH_FILE restrito, não por argumentos, e não usa refresh/logout; portanto não invente cookies ou CSRF para esse fluxo. A FastAPI indisponível não deve afetar readiness.

O smoke confirma persistência pela criação e consulta autenticadas; para comprovar sobrevivência ao restart, execute somente backend:

~~~bash
docker compose --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml restart backend
~~~

Após health e readiness UP, repita a consulta autenticada do mesmo id pelo procedimento do smoke. O registro permanece no Oracle, não no filesystem; não são necessários volumes de dados locais.

## Inspeção segura de logs

Antes de exibir logs, faça a checagem silenciosa abaixo. Ela usa cópia temporária restrita, compara valores sem imprimi-los e detecta nomes sensíveis e stack traces:

~~~bash
set +x
set -Eeuo pipefail
umask 077
log_file="$(mktemp)"
trap 'rm -f -- "${log_file}"' EXIT
docker compose --env-file /opt/energiai/config/backend.env \
  -f infra/deploy/oci/compose.yaml logs --no-color backend >"${log_file}"
log_check=0
for key in DB_URL DB_USERNAME DB_PASSWORD; do
  if ! value="$(sudo awk -v prefix="${key}=" '
      index($0, prefix) == 1 { print substr($0, length(prefix) + 1); found = 1; exit }
      END { if (!found) exit 1 }
    ' /opt/energiai/config/backend.env)"; then
    printf 'REPROVADO: variavel obrigatoria %s\n' "${key}" >&2; log_check=1
  elif [[ -z "${value}" ]]; then
    printf 'REPROVADO: variavel obrigatoria %s vazia\n' "${key}" >&2; log_check=1
  elif grep -Fq -- "${value}" "${log_file}"; then
    printf 'REPROVADO: valor de %s encontrado nos logs\n' "${key}" >&2; log_check=1
  else
    printf 'APROVADO: valor de %s ausente dos logs\n' "${key}"
  fi
  unset value
done
if grep -Eq '(^|[^[:alnum:]_])(DB_URL|DB_USERNAME|DB_PASSWORD)([^[:alnum:]_]|$)' "${log_file}"; then
  printf 'REPROVADO: nome de variavel sensivel encontrado nos logs\n' >&2; log_check=1
fi
if grep -Eq '(^|[[:space:]])at [[:alnum:]_.$]+\([^)]*:[0-9]+\)|Exception:|Caused by:' "${log_file}"; then
  printf 'REPROVADO: stack trace inesperado encontrado nos logs\n' >&2; log_check=1
fi
exit "${log_check}"
~~~

Somente após aprovação use logs --tail=100. Repita após falha ou atualização e não use docker inspect como diagnóstico comum, pois o ambiente do processo contém credenciais.

## Pós-deploy

No host OCI, aguarde health, liveness e readiness UP; execute o smoke autenticado pelo túnel ou no host; valide a classificação esperada e persistência; depois revise logs de forma segura, docker stats, docker system df e df -h. No ambiente OCI real, confirme ainda DNS para a instância, HTTP para HTTPS, certificado confiável, TCP 80/443 acessível, 8080 não público e CORS pela URL HTTPS. Compose/Caddy locais não provam essas condições.
