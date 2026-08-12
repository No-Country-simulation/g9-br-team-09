# Backend EnergiAI

## Visão geral

Este módulo implementa a API REST do EnergiAI: recebe dados de consumo, tenta obter a classificação e recomendações da API Python de Machine Learning, calcula o custo estimado, persiste a análise e a disponibiliza para consulta. Quando a integração de ML falha ou devolve dados inválidos, utiliza o classificador e as recomendações locais (`RULE_BASED_FALLBACK`).

Stack: Java 21, Spring Boot 4.0.7, Spring Web MVC, Spring Data JPA, Flyway, H2, Oracle JDBC Thin, Spring Boot Actuator e Springdoc OpenAPI 3.0.2.

## Estrutura do módulo

```text
backend/
├── src/main/java/.../backend/
│   ├── client/ml/       # cliente HTTP da API de ML
│   ├── config/          # Spring, ML, OpenAPI, H2 e Segurança
│   ├── controller/      # endpoints REST
│   ├── documentation/   # contrato OpenAPI
│   ├── dto/             # requests e responses (incluindo Auth)
│   ├── entity/          # entidade JPA (Análise e Usuário)
│   ├── enums/
│   ├── exception/
│   ├── mapper/
│   ├── repository/
│   └── service/         # Negócio, Fallback, JWT e Usuário
├── src/main/resources/
│   ├── application*.properties
│   └── db/migration/    # migrations Flyway (Tabelas e Segurança)
├── src/test/java/.../backend/
├── Dockerfile
├── compose.yaml
└── README.Docker.md
```

## Requisitos

- Java 21;
- Git, para obter o repositório;
- Maven Wrapper incluído no projeto — Maven global não é necessário;
- Docker e Docker Compose somente para execução em containers.

## Execução local

A partir da raiz do repositório, o profile `local` usa H2 em memória:

Linux, WSL e macOS:

```bash
cd backend
export JWT_SECRET="$(openssl rand -base64 32)"
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd backend
$env:JWT_SECRET="<segredo-base64-com-pelo-menos-256-bits>"
$env:SPRING_PROFILES_ACTIVE="local"
.\mvnw.cmd spring-boot:run
```

A aplicação inicia na porta `8080`, com context path `/api/v1`; a URL-base é `http://localhost:8080/api/v1`.

## Profiles

| Profile | Uso | Banco e persistência | Dependências externas |
|---|---|---|---|
| `local` | Desenvolvimento manual. | H2 em memória, Flyway habilitado, Hibernate `ddl-auto=none`; dados somem ao encerrar. | Não requer Oracle nem credenciais OCI. A API de ML é opcional porque há fallback local. |
| `test` | Suíte automatizada; não é profile usual de execução manual. | H2 em memória `energiai-test`, Flyway habilitado, console H2 desabilitado. | Não requer Oracle ou API Python real; testes isolam a integração quando necessário. |
| `oci` | Execução contra Oracle Autonomous Database. | Oracle via JDBC Thin/TLS, Flyway habilitado, Hibernate `ddl-auto=validate`. | Requer as variáveis OCI abaixo. Consulte [a documentação OCI](../docs/oracle-autonomous-database.md). |

### Execução com Oracle OCI

O profile `oci` requer variáveis fornecidas externamente e carregadas no mesmo processo que executa o Maven. Em Linux, WSL e macOS:

```bash
set -a
source ~/.config/energiai/oci.env
set +a

cd backend
./mvnw spring-boot:run
```

O arquivo externo deve fornecer, no mínimo:

```text
SPRING_PROFILES_ACTIVE=oci
DB_URL=<jdbc-thin-tls-url>
DB_USERNAME=<usuario-da-aplicacao>
DB_PASSWORD=<senha-da-aplicacao>
```

Não versione esse arquivo nem credenciais. Para Windows PowerShell, Docker, criação segura do arquivo externo e validação real da conexão, consulte o [guia operacional do Oracle Autonomous Database](../docs/oracle-autonomous-database.md).

## Segurança e Autenticação

A API utiliza **Spring Security** com **OAuth2 Resource Server** para autenticação stateless baseada em **JWT (JSON Web Token)**.

### Arquitetura

- **Algoritmo de Assinatura:** HS256 (Symmetric HMAC).
- **Codificação de Senha:** `DelegatingPasswordEncoder` com prefixo `{bcrypt}`.
- **Validação de Token:** Realizada de forma stateless em cada requisição protegida pelo header `Authorization: Bearer <token>`.
- **Restrição de Algoritmo:** O sistema rejeita tokens assinados com qualquer algoritmo diferente de HS256.

### Variáveis de Ambiente Obrigatórias

| Variável | Descrição | Padrão Local |
|---|---|---|
| `JWT_SECRET` | Segredo HMAC em Base64 (mín. 256 bits) | Obrigatório |
| `JWT_ISSUER` | Emissor do token | `energiai-api` |
| `JWT_AUDIENCE` | Público alvo do token | `energiai-frontend` |
| `JWT_ACCESS_TOKEN_EXPIRATION` | Duração do token (ex: 15m, 1h) | `15m` |

### Geração de Segredo Seguro

Para o ambiente local ou produção, gere um segredo de 256 bits codificado em Base64:

```bash
openssl rand -base64 32
```

### Estrutura do Access Token (Claims)

- sub: Identificador único do usuário (ID).
- iss: Emissor do token.
- aud: Público alvo.
- roles: Lista de permissões (ex: ["USER"]).
- jti: Identificador único do token (UUID).
- iat / exp: Timestamps de emissão e expiração.

### Normalização e Erros

- Normalização de Dados: O sistema realiza o trim() e a conversão para minúsculas do e-mail antes da validação (@Email) e da persistência, garantindo unicidade e evitando erros de entrada.
- Padronização de Erros: Falhas de segurança (token ausente, expirado ou inválido) retornam o objeto ApiErrorResponse com os códigos UNAUTHORIZED_ERROR (401) ou FORBIDDEN_ERROR (403), mantendo o contrato global da API.

## Endpoints

| Método e caminho                        | Descrição                                                                                                  |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------|
| `POST /api/v1/analise-energetica`       | Cria, classifica e persiste uma análise do usuário autenticado.                                           |
| `GET /api/v1/analise-energetica`        | Lista análises do usuário autenticado; aceita `page`, `size` e `sort`.                                    |
| `GET /api/v1/analise-energetica/{id}`   | Obtém detalhe pertencente ao usuário autenticado.                                                         |
| `GET /api/v1/analise-energetica/resumo` | Retorna indicadores do dashboard do usuário autenticado.                                                  |
| `GET /api/v1/actuator/health`           | Estado geral da aplicação.                                                                                 |
| `GET /api/v1/actuator/health/liveness`  | Estado do processo; não depende do banco ou da API de ML.                                                  |
| `GET /api/v1/actuator/health/readiness` | Prontidão para atender tráfego; inclui o banco obrigatório.                                                |
| `POST /api/v1/auth/register`            | Cadastra um novo usuário.                                                                                  |
| `POST /api/v1/auth/login`               | Autentica, retorna access token e emite refresh token em cookie.                                          |
| `POST /api/v1/auth/refresh`             | Renova a sessão por cookie, com rotação e proteção CSRF.                                                   |
| `POST /api/v1/auth/logout`              | Encerra a sessão por cookie, com proteção CSRF.                                                            |
| `GET /api/v1/auth/me`                   | Retorna o perfil do usuário autenticado.                                                                   |

### Exemplo de análise

O contrato público usa `snake_case`:

```json
{
  "consumo_kwh": 420,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 10,
  "tipo_imovel": "CASA",
  "horas_alto_consumo": 8
}
```

Valores aceitos para `tipo_imovel`: `CASA`, `APARTAMENTO`, `COMERCIO`, `ESCRITORIO`, `INDUSTRIA` e `OUTRO`.

```bash
export ACCESS_TOKEN='<access_token_obtido_no_login>'

curl --fail --request POST http://localhost:8080/api/v1/analise-energetica \
  --header "Authorization: Bearer ${ACCESS_TOKEN}" \
  --header 'Content-Type: application/json' \
  --data '{"consumo_kwh":420,"uso_horario_pico":true,"quantidade_equipamentos":10,"tipo_imovel":"CASA","horas_alto_consumo":8}'
```

Os endpoints de análise exigem `Authorization: Bearer <access_token>`. O
contrato completo de autenticação, refresh, logout e CSRF está em
[docs/api-contract.md](../docs/api-contract.md); o refresh token é enviado em
cookie e os endpoints de refresh/logout exigem o token CSRF correspondente.

### Exemplos de Autenticação

#### Cadastro (POST /api/v1/auth/register)

```bash
{
  "nome": "Lucas Rossoni",
  "email": "lucas@email.com",
  "senha": "senha-segura"
}
```

#### Login (POST /api/v1/auth/login)

```bash
{
  "email": "lucas@email.com",
  "senha": "senha-segura"
}
```

- Resposta de Sucesso no Login

```bash
{
  "access_token": "eyJhbGci...",
  "token_type": "Bearer",
  "expires_in": 900,
  "usuario": {
    "id": 1,
    "nome": "Lucas Rossoni",
    "email": "lucas@email.com",
    "role": "USER",
    "criado_em": "2026-08-02T16:35:32"
  }
}
```

### Perfil do Usuário (GET /api/v1/auth/me)

- Header: Authorization: Bearer <access_token>
- Resposta:

```bash
{
  "id": 1,
  "nome": "Lucas Rossoni",
  "email": "lucas@email.com",
  "role": "USER",
  "criado_em": "2026-08-02T16:35:32"
}
```

## Integração com Machine Learning

O backend chama `POST /predict` na API Python. A URL-base padrão é `http://localhost:8000`; os timeouts padrão são 2 segundos para conexão e 5 segundos para leitura. Uma resposta válida produz `fonte_classificacao: ML_MODEL`. Indisponibilidade, erro HTTP, ausência de corpo ou dados inválidos acionam a classificação/recomendação local e retornam `RULE_BASED_FALLBACK`.

O repositório contém o cliente dessa integração; este documento não pressupõe que uma API Python esteja em execução.

## Swagger, OpenAPI e health checks

- Swagger UI: `http://localhost:8080/api/v1/swagger-ui/index.html`
- OpenAPI: `http://localhost:8080/api/v1/v3/api-docs`
- Health geral: `http://localhost:8080/api/v1/actuator/health`
- Liveness: `http://localhost:8080/api/v1/actuator/health/liveness`
- Readiness: `http://localhost:8080/api/v1/actuator/health/readiness`

O Swagger está configurado para suportar autenticação Bearer. Use o botão **Authorize** para colar o JWT obtido no login.

Somente o endpoint `health` do Actuator é exposto, incluindo seus grupos de health. Detalhes e componentes internos permanecem ocultos em todas as respostas, portanto URLs JDBC, credenciais, stack traces e outros dados sensíveis não são retornados.

No profile `oci`, o liveness contém somente o estado de liveness do processo e não consulta Oracle, FastAPI ou outro serviço externo. O readiness contém o estado de readiness e o indicador `db`, pois Oracle é obrigatório para persistência. A FastAPI não integra a readiness: a indisponibilidade dela mantém o fallback local `RULE_BASED_FALLBACK` disponível.

```bash
curl --fail http://localhost:8080/api/v1/actuator/health
curl --fail http://localhost:8080/api/v1/actuator/health/liveness
curl --fail http://localhost:8080/api/v1/actuator/health/readiness
```

Resposta saudável:

```json
{
  "status": "UP"
}
```

## Banco de dados

No profile `local`, o H2 em memória usa o modo Oracle. Flyway aplica as migrations e Hibernate não gera DDL. Os dados são descartados ao encerrar a aplicação. O console H2 está em `http://localhost:8080/api/v1/h2-console`, com:

```text
JDBC URL: jdbc:h2:mem:energiai;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
User Name: sa
Password: vazio
```

Use no console exatamente a URL configurada no datasource. Nos logs de inicialização, confirme que o Flyway validou ou aplicou a migration `V1__create_energy_analysis_table.sql`. No H2 Console, confirme a existência das tabelas `energy_analysis` e `flyway_schema_history`. Para validar persistência, obtenha um access token, envie uma análise autenticada pelo `POST /api/v1/analise-energetica` e consulte o ID retornado com o mesmo Bearer em `GET /api/v1/analise-energetica/{id}`.

No profile `oci`, o backend usa Oracle Autonomous Database por JDBC Thin com TLS. Não há credenciais no repositório: use o modelo [`.env.example`](../.env.example) e siga o [guia operacional OCI](../docs/oracle-autonomous-database.md), sem copiar credenciais para o workspace.

O guia OCI concentra a criação do arquivo externo `~/.config/energiai/oci.env` (ou `$HOME\.config\energiai\oci.env` no PowerShell), o carregamento na sessão atual, Docker com `--env-file` e o teste opt-in `OracleAutonomousDatabaseIntegrationTest`. Para validar o ambiente implantado pela API pública, use o [smoke test autenticado](../infra/tests/smoke/README.md). O teste opt-in usa banco externo, confirma Oracle/Flyway/tabelas e remove o registro criado; não faz parte da suíte padrão e não deve ser executado sem autorização.

## Variáveis de ambiente

| Variável | Finalidade | Padrão |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Profile Spring ativo. | Sem profile explícito. |
| `ML_API_BASE_URL` | URL-base da API de ML. | `http://localhost:8000` |
| `ML_API_CONNECT_TIMEOUT` | Timeout de conexão da ML. | `2s` |
| `ML_API_READ_TIMEOUT` | Timeout de leitura da ML. | `5s` |

Exclusivas do profile `oci`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_CONNECT_RETRIES`, `DB_POOL_MIN_IDLE`, `DB_POOL_MAX_SIZE`, `DB_CONNECTION_TIMEOUT_MS`, `DB_VALIDATION_TIMEOUT_MS` e `DB_KEEPALIVE_TIME_MS`. As três primeiras são obrigatórias; as demais possuem defaults no profile.

`DB_CONNECT_RETRIES` padrão é `5`; `DB_POOL_MIN_IDLE` é `1`; `DB_POOL_MAX_SIZE` é `5`; `DB_CONNECTION_TIMEOUT_MS`, `DB_VALIDATION_TIMEOUT_MS` e `DB_KEEPALIVE_TIME_MS` são milissegundos, com defaults `30000`, `5000` e `120000`. Em OCI, `DB_URL` deve ser uma string JDBC Thin TLS sem wallet, sem aspas incorporadas, carregada no mesmo processo do Maven. Falhas de datasource normalmente indicam variáveis ausentes, URL inválida, credenciais/privilégio `CREATE SESSION` ou ACL/rede. Não conceda `DBA`, não use `flyway clean` em schema persistente e não altere `ddl-auto` para `update` para mascarar divergências de schema.

## Troubleshooting

- Profile ou datasource ausente: confira `echo "$SPRING_PROFILES_ACTIVE"` ou `$env:SPRING_PROFILES_ACTIVE`; `local` não depende de Oracle e `oci` exige suas variáveis.
- H2 Console não conecta: confirme aplicação em execução, profile `local`, context path, URL JDBC completa, usuário `sa` e senha vazia.
- Falha de Flyway ou Hibernate `validate`: revise logs, `flyway_schema_history`, migration aplicada, usuário/schema da conexão e tipos esperados. Flyway é a fonte de verdade do schema; Hibernate não deve criá-lo nem atualizá-lo.
- H2 em modo Oracle não substitui validação no Oracle real: diferenças de sintaxe e tipos podem existir.

## Testes

Os testes usam H2/Flyway e mocks ou cenários controlados para ML; a suíte padrão não exige Oracle real nem uma API Python real. O teste Oracle real é opt-in e não faz parte destes comandos.

Linux, WSL e macOS:

```bash
cd backend
./mvnw test
./mvnw clean test
```

Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd clean test
```

## Docker

Consulte [README.Docker.md](README.Docker.md) para detalhes. A partir de `backend`:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
docker compose build
docker compose up -d
docker compose ps
docker compose logs -f backend
docker compose down
```

Execução sem Compose:

```bash
docker build -t energiai-backend:local .

docker run --rm \
  --name energiai-backend \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e JWT_SECRET \
  energiai-backend:local
```
