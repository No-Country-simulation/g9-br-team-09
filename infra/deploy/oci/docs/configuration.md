# Configuração do ambiente OCI

Este guia configura o ambiente externo antes do Compose. Use [`.env.example`](../.env.example) somente como referência: contém nomes e formatos fictícios, nunca valores reais.

## Diretórios e arquivo de ambiente

O host usa `/opt/energiai/repository` para checkout, `/opt/energiai/config/backend.env` para ambiente externo e `/opt/energiai/images` para TARs. Não clone sobre `config`, `logs` ou `data`. Crie `images` sem permissões recursivas:

```bash
sudo install -d -o ubuntu -g "$(id -gn ubuntu)" -m 0750 /opt/energiai/images
```

Crie `backend.env` apenas se ausente; recuse links e outros tipos. A segunda execução preserva o conteúdo e corrige somente metadados:

```bash
env_path=/opt/energiai/config/backend.env
admin_group="$(id -gn ubuntu)"
if [[ -L "${env_path}" || ( -e "${env_path}" && ! -f "${env_path}" ) ]]; then
  printf 'REPROVADO: caminho de ambiente inseguro: %s\n' "${env_path}" >&2; exit 1
fi
if [[ ! -e "${env_path}" ]]; then
  sudo install -o ubuntu -g "${admin_group}" -m 0600 /dev/null "${env_path}"
else
  sudo chown ubuntu:"${admin_group}" "${env_path}"; sudo chmod 0600 "${env_path}"
fi
sudoedit /opt/energiai/config/backend.env
sudo stat --format='owner=%U group=%G mode=%a' /opt/energiai/config/backend.env
```

O resultado deve indicar administrador, grupo primário e modo `600`. Não use `set -x`, `cat`, `less`, `head`, `tail` ou `docker compose config` sem `--quiet` sobre o arquivo. Não o copie ao repositório.

## Variáveis e secrets

Defina `BACKEND_IMAGE` com tag imutável, `API_PUBLIC_HOSTNAME` sem esquema, porta, caminho ou IP em arquivo versionado e `SPRING_PROFILES_ACTIVE=oci`. Defina também `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_CONNECT_RETRIES`, `DB_POOL_MIN_IDLE`, `DB_POOL_MAX_SIZE`, `DB_CONNECTION_TIMEOUT_MS`, `DB_VALIDATION_TIMEOUT_MS`, `DB_KEEPALIVE_TIME_MS`, `JAVA_TOOL_OPTIONS`, `ML_API_BASE_URL`, `ML_API_CONNECT_TIMEOUT`, `ML_API_READ_TIMEOUT`, `CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_ACCESS_TOKEN_EXPIRATION`, `AUTH_REFRESH_TOKEN_EXPIRATION`, `AUTH_REFRESH_FAMILY_EXPIRATION`, `AUTH_REFRESH_REUSE_GRACE_PERIOD` e `AUTH_REFRESH_COOKIE_*`. `BACKEND_ENV_FILE` permanece vazio na OCI, salvo validação local controlada.

Gere JWT exclusivo com `openssl rand -base64 32`, transfira-o por canal seguro, informe-o apenas por `sudoedit` e execute `unset JWT_SECRET`. O backend rejeita segredo ausente, Base64 inválido ou menor que 256 bits. Nunca inclua senhas, tokens, wallets, JDBC real ou o conteúdo de `backend.env` no Git, tickets ou logs.

Mantenha `AUTH_REFRESH_COOKIE_SECURE=true`, `AUTH_REFRESH_COOKIE_SAME_SITE=None`, path `/api/v1/auth` e domínio vazio para cookie host-only, salvo validação operacional. O frontend deve enviar `credentials: "include"` e usar `X-XSRF-TOKEN`; não deve ler o cookie por `document.cookie`. Cookies de terceiros podem ser bloqueados mesmo com `SameSite=None; Secure`.

## Oracle, JVM e ML

`DB_URL` é JDBC Thin TLS sem wallet no formato conceitual `jdbc:oracle:thin:@tcps://<host>:<porta>/<service-name>`. A conta recebe apenas privilégios do schema, nunca `DBA`. Valor inicial JVM:

```text
-XX:MaxRAMPercentage=55.0 -XX:InitialRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError
```

Confirme sob carga real. Para fallback sem FastAPI, use `ML_API_BASE_URL=http://127.0.0.1:9`: loopback indisponível do container, com timeouts conservadores.

## Hostname, HTTPS, CORS e frontend

Para IP efêmero, use `<CURRENT_OCI_PUBLIC_IP>.sslip.io` somente em `API_PUBLIC_HOSTNAME` externo. Caddy usa essa variável e pode gerir redirect HTTP e certificados quando DNS, TCP 80/443 e ACME estiverem acessíveis. Troca de IP exige revisar hostname, links e certificado. O profile `oci` usa headers de proxy para Swagger/OpenAPI HTTPS.

Para o frontend público atual, defina a origem exata sem caminho. Se o hostname da Vercel mudar, atualize apenas o arquivo externo de ambiente:

```text
CORS_ALLOWED_ORIGINS=https://energiai.vercel.app
VITE_API_BASE_URL=https://<API_PUBLIC_HOSTNAME>/api/v1
```

Separe origens por vírgula; jamais use `*`. Recrie backend após CORS e reimplante Vite após variável de build. Vercel pertence à Issue #119; evidências reais estão em [produção](production-validation.md).
