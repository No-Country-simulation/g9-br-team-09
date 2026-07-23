# Oracle Autonomous Database

## Estratégia adotada

O backend conecta ao Oracle Autonomous Database com Oracle JDBC Thin e TLS sem wallet. A conexão é criptografada, mantém a configuração sensível fora do repositório e evita a distribuição e rotação de wallets no MVP. A instância ADB deve aceitar TLS sem wallet; mTLS com wallet é apenas uma alternativa futura caso essa modalidade não esteja disponível.

O profile `local` continua usando H2 em memória no modo Oracle. O profile `oci` usa Oracle ADB e não habilita o console H2.

## Pré-requisitos OCI

- Autonomous Database provisionado para Transaction Processing, com TLS sem wallet habilitado.
- ACL ou rede permitindo a máquina de execução ou OCI Compute.
- String TLS obtida no OCI Console, sem publicá-la.
- Usuário dedicado `ENERGIAI_APP`, nunca `ADMIN`, com privilégios mínimos: `CREATE SESSION`, criação e DML nas próprias tabelas, capacidade de manter `flyway_schema_history` e quota suficiente no tablespace aplicável.
- Java 21 e o driver JDBC Oracle já presentes no backend.

A criação do usuário e as concessões devem ser feitas por um administrador de banco seguindo privilégio mínimo; não conceda `DBA` à aplicação.

## Configuração segura

`.env.example` é somente um modelo versionado com placeholders seguros. Credenciais reais devem ficar fora do repositório: em Linux, WSL e macOS, use `~/.config/energiai/oci.env`; no Windows PowerShell, use `$HOME\.config\energiai\oci.env`. Nunca copie ou versione credenciais reais.

Forneça externamente `SPRING_PROFILES_ACTIVE=oci`, `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. A URL deve ser a string TLS sem wallet fornecida pelo OCI Console, com o prefixo JDBC Thin. Não publique senhas, URL completa, host, service name, OCIDs, tokens, wallets, keystores ou truststores.

As regras `.env`, `.env.*` e `!.env.example` no `.gitignore` são defesa em profundidade contra a criação acidental de arquivos sensíveis no repositório.

## Linux / WSL / macOS

Crie o arquivo externo e restrinja suas permissões:

```bash
mkdir -p ~/.config/energiai

cp .env.example ~/.config/energiai/oci.env

chmod 600 ~/.config/energiai/oci.env
```

Carregue as variáveis no processo atual do terminal:

```bash
set -a
source ~/.config/energiai/oci.env
set +a
```

Execute o Maven:

```bash
cd backend
./mvnw spring-boot:run
```

Para o teste real opt-in, carregue as variáveis e execute:

```bash
set -a
source ~/.config/energiai/oci.env
set +a

cd backend

RUN_ORACLE_IT=true \
./mvnw -Dtest=OracleAutonomousDatabaseIntegrationTest test
```

## Windows PowerShell

Crie o arquivo externo:

```powershell
New-Item -ItemType Directory `
  -Force `
  "$HOME\.config\energiai"

Copy-Item `
  ".env.example" `
  "$HOME\.config\energiai\oci.env"
```

Carregue as variáveis no processo atual do PowerShell:

```powershell
$envFile = "$HOME\.config\energiai\oci.env"

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()

    if ($line -and -not $line.StartsWith("#")) {
        $name, $value = $line -split "=", 2
        $value = $value.Trim().Trim("'").Trim('"')

        Set-Item `
            -Path "Env:$($name.Trim())" `
            -Value $value
    }
}
```

Essas variáveis existem apenas no processo atual do PowerShell. Execute o Maven:

```powershell
Set-Location backend

.\mvnw.cmd spring-boot:run
```

Para o teste real opt-in:

```powershell
$env:RUN_ORACLE_IT = "true"

.\mvnw.cmd `
  -Dtest=OracleAutonomousDatabaseIntegrationTest `
  test

Remove-Item Env:RUN_ORACLE_IT
```

## Docker multiplataforma

O Docker recebe as variáveis diretamente por `--env-file`; não é necessário carregá-las previamente no terminal.

Linux / WSL:

```bash
docker build \
  -t energiai-backend:oci \
  backend

docker run --rm \
  --env-file "$HOME/.config/energiai/oci.env" \
  -p 8080:8080 \
  energiai-backend:oci
```

Windows PowerShell:

```powershell
docker build `
  -t energiai-backend:oci `
  backend

$envFile = (Resolve-Path `
  "$HOME\.config\energiai\oci.env").Path

docker run --rm `
  --env-file "$envFile" `
  -p 8080:8080 `
  energiai-backend:oci
```

## Verificação pela API

Após a aplicação iniciar, a verificação de persistência usa somente a API pública.

Linux / WSL / macOS (requer `curl` e `jq`):

```bash
bash infra/scripts/verify-oracle-adb.sh
```

Windows PowerShell:

```powershell
.\infra\scripts\verify-oracle-adb.ps1
```

Os scripts fazem `POST /api/v1/analise-energetica`, extraem o ID e consultam `GET /api/v1/analise-energetica/{id}`, validando todos os campos persistidos. No Bash, defina opcionalmente `BASE_URL`; no PowerShell, passe opcionalmente `-BaseUrl`.

O health check disponível é:

```bash
curl --fail http://localhost:8080/api/v1/actuator/health
```

## Ajustes de compatibilidade identificados na validação real

A validação contra o Oracle Autonomous Database revelou diferenças entre o comportamento do H2 em modo Oracle e o Oracle real.

### Migration V1

A definição original da coluna `created_at` era:

```sql
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
```

O Oracle retornou `ORA-03076`, pois a cláusula `DEFAULT` deve aparecer antes da constraint inline `NOT NULL`.

A definição foi corrigida para:

```sql
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
```

A alteração foi realizada na V1 porque sua primeira execução no Autonomous Database falhou antes da criação de `ENERGY_ANALYSIS`. Nenhum schema persistente compartilhado possuía essa migration aplicada com sucesso.

### Mapeamentos JPA

O Hibernate 7, ao utilizar o dialect Oracle, esperava tipos diferentes dos definidos deliberadamente pela migration:

- Atributos Java `Double` eram interpretados como `BINARY_DOUBLE`, enquanto o schema utiliza `NUMBER`/`DECIMAL` com precisão e escala fixas.
- O atributo Java `Boolean` era interpretado como `BOOLEAN`, enquanto o schema utiliza `NUMBER(1)` com valores `0` e `1`.

Para manter o schema existente e a precisão decimal:

- `consumoKwh` utiliza `SqlTypes.NUMERIC` com precisão 10 e escala 2.
- `probabilidade` utiliza `SqlTypes.NUMERIC` com precisão 5 e escala 2.
- `usoHorarioPico` utiliza `NumericBooleanConverter`, preservando o armazenamento numérico `0`/`1`.

Essas alterações afetam somente o mapeamento ORM da entidade.

Não foram alterados:

- DTOs;
- contrato JSON;
- endpoints;
- regras de classificação;
- cálculo de custo;
- integração com Machine Learning.

## Teste real opt-in

`OracleAutonomousDatabaseIntegrationTest` fica desativado por padrão e só executa com `RUN_ORACLE_IT=true`, usando as credenciais externas já autorizadas no ambiente. Ele confirma o produto Oracle via JDBC, a migration V1/Flyway, a tabela `ENERGY_ANALYSIS` no schema atual da conexão, um `POST` e o `GET` do mesmo registro. A integração ML é mockada e o registro criado é removido ao fim do teste.

Não o execute sem autorização explícita para uso do banco real.

## Evidência sanitizada

Registre data e ambiente da validação, SHA do commit, profile ativo, produto JDBC, versão Flyway, confirmação de `ENERGY_ANALYSIS`, respostas sanitizadas de POST/GET com o ID e o resultado do teste opt-in. Capturas e logs devem omitir todos os dados de conexão e credenciais.
