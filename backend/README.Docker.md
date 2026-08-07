# Backend EnergiAI com Docker

Este documento apresenta os comandos básicos para construir e executar o backend do EnergiAI com Docker.

## Pré-requisitos

- Docker;
- Docker Compose.

Verifique a instalação:

```bash
docker --version
docker compose version
```

## Construir a imagem

Execute na pasta `backend`:

```bash
docker compose build
```

Para reconstruir sem utilizar cache:

```bash
docker compose build --no-cache
```

A imagem será criada como:

```text
energiai-backend:local
```

## Executar a aplicação

### Docker local

O `compose.yaml` exige `JWT_SECRET`. Gere um segredo local antes de iniciar:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

```bash
docker compose up
```

Para executar em segundo plano:

```bash
docker compose up -d
```

O container utiliza o profile Spring `local`, configurado em runtime pelo `compose.yaml`.

## Endereços

| Recurso | URL |
|---|---|
| API | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/api/v1/swagger-ui/index.html` |
| Health check | `http://localhost:8080/api/v1/actuator/health` |

Validação do health check:

```bash
curl http://localhost:8080/api/v1/actuator/health
```

Resposta esperada:

```json
{
  "status": "UP"
}
```

## Visualizar logs

```bash
docker compose logs -f backend
```

## Parar a aplicação

```bash
docker compose down
```

## Reconstruir após alterações

```bash
docker compose up --build
```

## Executar sem Docker Compose

Construir a imagem:

```bash
docker build -t energiai-backend:local .
```

Executar o container:

```bash
docker run --rm \
  --name energiai-backend \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e JWT_SECRET \
  energiai-backend:local
```

## Imagem publicada no Docker Hub

Após uma integração na branch `develop`, o workflow **Backend Docker** constrói,
valida e publica a imagem no Docker Hub. Para obter a imagem mais recente
publicada dessa branch:

```bash
docker pull pxs00/energiai-backend:develop
```

Execute-a com o profile local:

```bash
docker run --rm \
  --name energiai-backend \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e JWT_SECRET \
  pxs00/energiai-backend:develop
```

As tags publicadas têm finalidades diferentes:

- `develop` é mutável e representa a imagem mais recente publicada após uma
  integração na branch `develop`.
- `sha-<commit-curto>` é imutável, identifica uma revisão específica e é
  recomendada para rastreabilidade e reprodução. Por exemplo:

  ```bash
  docker pull pxs00/energiai-backend:sha-abcdef1
  ```

Não há publicação automática da tag `latest`. Pull Requests apenas constroem e
validam a imagem; pushes para `develop` publicam as duas tags; e execuções por
`workflow_dispatch` executam somente a validação.

As credenciais Oracle não fazem parte da imagem. A implantação operacional do
backend na OCI já usa configuração externa em runtime e imagens imutáveis; ela
é diferente da execução Docker local e está documentada no
[runbook OCI](../infra/deploy/oci/README.md). A publicação de uma imagem no
Docker Hub não equivale, por si só, a uma implantação na OCI.

## Variáveis de ambiente

As configurações devem ser fornecidas durante a execução do container.

| Variável | Descrição | Valor padrão |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Profile ativo do Spring Boot | `local` no Compose |
| `JWT_SECRET` | Segredo Base64 usado para assinar e validar JWT. | Obrigatório; sem valor padrão. |
| `ML_API_BASE_URL` | URL-base da API de Machine Learning | `http://localhost:8000` |
| `ML_API_CONNECT_TIMEOUT` | Timeout de conexão com a API de ML | `2s` |
| `ML_API_READ_TIMEOUT` | Timeout de leitura da API de ML | `5s` |

## Segurança

A imagem não deve incorporar:

- arquivos `.env`;
- credenciais;
- tokens;
- chaves privadas;
- arquivos sensíveis da OCI.

Dados sensíveis devem ser fornecidos por variáveis de ambiente ou pelo mecanismo de secrets do ambiente de deploy.
