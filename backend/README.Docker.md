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
  energiai-backend:local
```

## Variáveis de ambiente

As configurações devem ser fornecidas durante a execução do container.

| Variável | Descrição | Valor padrão |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Profile ativo do Spring Boot | `local` no Compose |
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