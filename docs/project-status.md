# Status do Projeto — EnergiAI

## Visão geral

Este documento registra o estado consolidado do projeto após as entregas
técnicas finais do hackathon. A documentação final de entrega está organizada
em `docs/delivery/`. Ele não substitui o [contrato HTTP](api-contract.md) nem
os runbooks operacionais.

## Estado atual

| Frente | Estado observado | Fonte principal |
| --- | --- | --- |
| Backend | API Spring Boot com autenticação, autorização, cálculo de custo, orquestração FastAPI, fallback local e persistência por usuário. Implantada em OCI Compute atrás do Caddy/HTTPS. | [backend/README.md](../backend/README.md) |
| Frontend | SPA React/TypeScript/Vite publicada na Vercel, com cadastro, login, renovação de sessão, rotas protegidas, análise, resultado, histórico, detalhe e dashboard. | [frontend/README.md](../frontend/README.md) |
| Data Science | FastAPI interna publicada no Render, com `/health` e `/predict`, servindo o modelo V2 `energy-classifier-v2`. | [README.API.md](../data-science/README.API.md) |
| Modelo | Dataset sintético V2, solução congelada Random Forest com calibração isotônica e avaliação oficial única do holdout. | [relatório V2](../data-science/docs/modeling-final-report-v2.md) |
| Persistência | Oracle Autonomous Database, Flyway e ownership das análises pelo usuário autenticado. | [guia Oracle](oracle-autonomous-database.md) |
| E2E | Suíte Playwright integrada em `develop`; valida fluxo publicado pelo navegador, incluindo ML e fallback controlado. | [frontend/e2e/README.md](../frontend/e2e/README.md) |
| Documentação | Contrato, operação e documentação específica de cada componente estão separados por responsabilidade. | [contrato HTTP](api-contract.md) |

As issues #86 (modelagem V2), #111 (FastAPI no Render) e #118 (autenticação no
frontend) estão tecnicamente concluídas e integradas. A entrega técnica da
#121 (E2E) também está integrada em `develop` no commit
`e5f4d126479de8842dfb2332a7f973c8e5a1f626`, sem inferir o fechamento
administrativo da issue. A documentação final de entrega está consolidada em
`docs/delivery/`, com arquitetura implantada e roteiro do Demo Day.

## Fluxo de classificação e disponibilidade

O frontend consome exclusivamente a API Spring Boot. Em uma resposta válida da
FastAPI, a análise é marcada como `ML_MODEL`. Falha, timeout, erro HTTP ou
resposta inválida da FastAPI acionam a classificação local
`RULE_BASED_FALLBACK`; esse caminho preserva a disponibilidade do backend, mas
não é Machine Learning. A FastAPI não faz parte da readiness do backend.

O custo mensal é calculado pelo backend com a tarifa de referência do MVP de
`R$ 0,75/kWh`. O contrato normativo de campos, enums e rotas permanece em
[api-contract.md](api-contract.md).

## Validação E2E registrada

A aplicação publicada validada corresponde ao snapshot de `main`
`196246909ef953085d35ce57b02da6285ab3a47f`. A integração posterior da suíte
E2E em `develop` é o commit
`e5f4d126479de8842dfb2332a7f973c8e5a1f626`; são referências distintas.

- execução normal com `E2E_EXPECTED_SOURCE=ML_MODEL`: `2 passed`, `1 skipped`,
  fonte observada `ML_MODEL`;
- fallback controlado com `E2E_EXPECTED_SOURCE=RULE_BASED_FALLBACK`: `3 passed`,
  fonte observada `RULE_BASED_FALLBACK`;
- após restaurar a configuração, nova execução com `ML_MODEL`: `2 passed`,
  `1 skipped`, fonte observada `ML_MODEL`.

O procedimento, as restrições de segurança e os limites dessa evidência estão
no [README E2E](../frontend/e2e/README.md). Ela não substitui testes unitários,
integração de componentes, smoke test operacional ou validação direta do Oracle.

## Limitações atuais

- O dataset é sintético; as métricas não comprovam validade externa,
  causalidade, desempenho em dados reais ou economia real.
- A tarifa `R$ 0,75/kWh` é uma referência do MVP, não uma tarifa universal.
- A FastAPI é uma dependência externa e pode sofrer cold start. O fallback
  mantém a análise disponível, mas não equivale ao modelo V2.
- As contas e análises descartáveis criadas pela suíte E2E permanecem
  persistidas porque não há exclusão pública pela interface.
- O hostname da API OCI depende de IP público efêmero via `sslip.io`.

## Referências operacionais e históricas

- [Implantação e operação OCI](../infra/deploy/oci/README.md) e [smoke test](../infra/tests/smoke/README.md) são fontes operacionais do backend.
- [Atas](meetings.md) registram decisões e pendências no momento de cada
  reunião; não representam o estado vivo acima.
- [Especificação V2](../data-science/docs/dataset-model-specification-v2.md)
  e [revisão do target](../data-science/docs/target-generation-review-v2.md)
  preservam planejamento metodológico anterior. Para a solução final, use o
  relatório V2.
