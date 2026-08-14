# EnergiAI

O EnergiAI é um MVP que transforma dados simples de consumo elétrico em uma
classificação de perfil energético, recomendações de uso e uma estimativa de
custo mensal. Ele apoia residências e pequenos estabelecimentos a enxergarem
hábito, horário de pico e quantidade de equipamentos como sinais para decisões
de consumo mais conscientes.

## Acesso

- Aplicação web: [energiai.vercel.app](https://energiai.vercel.app)
- API pública Spring Boot: [Swagger UI](https://147.15.30.0.sslip.io/api/v1/swagger-ui/index.html), [OpenAPI](https://147.15.30.0.sslip.io/api/v1/v3/api-docs) e [health](https://147.15.30.0.sslip.io/api/v1/actuator/health)

O hostname público da API usa `sslip.io` sobre um IP efêmero da instância OCI.
Se a instância receber outro IP, os links precisam ser atualizados.

## O que foi entregue

- SPA em React, TypeScript e Vite, publicada na Vercel, com autenticação,
  análise, resultado, histórico, detalhe e dashboard;
- API pública em Java 21 e Spring Boot, implantada em OCI Compute atrás do
  Caddy com HTTPS;
- persistência em Oracle Autonomous Database, com schema gerenciado por Flyway
  e análises isoladas por usuário autenticado;
- inferência interna FastAPI no Render com o modelo V2;
- fallback local do backend para manter a análise disponível quando a FastAPI
  falha, expira ou devolve resposta inválida.

O fluxo normal é `frontend → Spring Boot → FastAPI → modelo V2 → Spring Boot
→ Oracle → frontend`. O frontend não chama `/predict`: Spring Boot é a única
fronteira pública, calcula o custo de referência do MVP (`R$ 0,75/kWh`) e
persiste a análise. Respostas provenientes da FastAPI usam
`ML_MODEL`; o caminho de contingência usa `RULE_BASED_FALLBACK` e não é Machine
Learning.

## Documentação

Comece pela [documentação final de entrega](docs/delivery/README.md), que
resume a arquitetura, limites e roteiro do Demo Day.

| Fonte | Conteúdo |
| --- | --- |
| [Contrato HTTP](docs/api-contract.md) | Fonte normativa da API pública e da integração interna FastAPI. |
| [Decisões arquiteturais](docs/architecture-decisions.md) | Decisões transversais registradas. |
| [Status vivo do projeto](docs/project-status.md) | Estado observado em `develop`, separado da consolidação de entrega. |
| [Backend](backend/README.md) | Execução, segurança, persistência e integração. |
| [Frontend](frontend/README.md) | Desenvolvimento da SPA e deploy na Vercel. |
| [E2E](frontend/e2e/README.md) | Procedimento e resultados da validação pelo navegador. |
| [Relatório de modelagem V2](data-science/docs/modeling-final-report-v2.md) | Metodologia, artefato, métricas e limites do modelo. |
| [Operação OCI](infra/deploy/oci/README.md) | Implantação, operação, rollback e smoke tests. |

## Limites do MVP

O dataset e a avaliação do modelo são sintéticos. As métricas demonstram a
capacidade de reproduzir padrões dessa base nas condições testadas; não
comprovam desempenho em dados reais, validade externa, causalidade ou economia
real. A tarifa de `R$ 0,75/kWh` é somente uma referência do MVP. A FastAPI é
uma dependência externa sujeita a cold start; o fallback preserva disponibilidade,
mas não equivale ao modelo de ML.

## Execução local

Para iniciar uma cópia local, siga as instruções atualizadas de cada componente:
[backend Spring Boot](backend/README.md), [frontend](frontend/README.md) e
[FastAPI/Data Science](data-science/README.API.md). O contrato de rotas,
campos `snake_case` e enums está em [docs/api-contract.md](docs/api-contract.md).
