# Entrega final — EnergiAI

O EnergiAI é um MVP para tornar o consumo de energia mais compreensível. A
pessoa informa dados simples de uso e recebe uma categoria de perfil,
recomendações e uma estimativa mensal, ajudando a identificar oportunidades de
uso mais consciente em residências e pequenos estabelecimentos.

## O que foi entregue

- frontend React, TypeScript e Vite publicado na [Vercel](https://energiai.vercel.app);
- backend Java 21 e Spring Boot em OCI Compute, publicado por Caddy com HTTPS;
- Oracle Autonomous Database com Flyway e análises vinculadas ao usuário
  autenticado;
- inferência FastAPI no Render com o modelo V2;
- autenticação, análise, resultado, histórico, detalhe, dashboard e E2E pelo
  navegador;
- fallback local para manter o backend operacional quando a FastAPI falha.

O frontend acessa somente Spring Boot. O backend calcula o custo de referência
do MVP, orquestra a inferência, persiste a análise e devolve a resposta. No
fluxo ML-first, quando a FastAPI responde validamente, a fonte é `ML_MODEL`;
quando a integração falha, expira ou retorna uma resposta inválida, o backend
utiliza `RULE_BASED_FALLBACK`. Esse fallback é resiliência, não Machine Learning.

## Acesso e navegação

- Aplicação: [energiai.vercel.app](https://energiai.vercel.app)
- API pública: [Swagger](https://147.15.30.0.sslip.io/api/v1/swagger-ui/index.html), [OpenAPI](https://147.15.30.0.sslip.io/api/v1/v3/api-docs) e [health](https://147.15.30.0.sslip.io/api/v1/actuator/health)
- [Arquitetura implantada](architecture.md)
- [Roteiro de apresentação do Demo Day](demo-script.md)

O hostname público da API usa `sslip.io` e IP público efêmero; ele deve ser
reconfirmado se a instância OCI for recriada.

## Limitações importantes

O modelo foi treinado e avaliado com dados sintéticos. Seus resultados medem a
capacidade de reproduzir padrões dessa base sob as condições testadas; não
comprovam desempenho em dados reais, causalidade, validade externa ou economia
real. A tarifa de `R$ 0,75/kWh` é uma referência do MVP. A FastAPI pode ter
cold start; seu fallback preserva a disponibilidade, mas não é equivalente ao
modelo.

## Fontes detalhadas

- [Contrato HTTP normativo](../api-contract.md)
- [Decisões arquiteturais](../architecture-decisions.md)
- [Relatório final de modelagem V2](../../data-science/docs/modeling-final-report-v2.md)
- [Validação E2E](../../frontend/e2e/README.md)
- [Implantação e operação OCI](../../infra/deploy/oci/README.md)
- [Deploy do frontend](../../frontend/DEPLOY.md)

O [status do projeto](../project-status.md) acompanha o estado vivo de
`develop`; esta pasta resume a entrega do hackathon sem substituir essas fontes.
