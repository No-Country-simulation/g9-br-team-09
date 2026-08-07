# Status do Projeto — EnergIAI

## Visão geral

O EnergIAI analisa dados de consumo elétrico, classifica o perfil energético em `EFICIENTE`, `MODERADO` ou `INEFICIENTE`, gera recomendações, estima o custo mensal e disponibiliza os resultados pela API REST.

Este documento registra o estado do projeto observado em `develop`. Os contratos HTTP são mantidos em [docs/api-contract.md](api-contract.md); ele não substitui a documentação operacional, os smoke tests nem a consolidação final da entrega.

## Status geral

| Frente | Status | Estado atual e principal pendência |
| --- | --- | --- |
| Backend | Parcial | API Spring Boot, autenticação, persistência por usuário, cliente FastAPI e fallback local estão implementados. A validação ponta a ponta com o modelo oficial permanece pendente. |
| Frontend | Parcial | SPA publicada na Vercel, com formulário, resultado, histórico, detalhe, dashboard e cliente HTTP. O fluxo integrado de cadastro, login, refresh, logout e rotas protegidas permanece pendente na #118. |
| Data Science / FastAPI | Parcial | A FastAPI interna, `GET /health`, `POST /predict` e o contrato com Spring Boot estão implementados. A modelagem V2 oficial está em desenvolvimento na #86 e o deploy está pendente na #111. |
| Cloud / OCI | Parcial | O backend containerizado, Oracle Autonomous Database, Caddy/HTTPS, operação de deploy e smoke tests autenticados possuem documentação e automação versionadas. A FastAPI não integra esse deploy. |
| Documentação | Parcial | O contrato de APIs foi consolidado na #70 e os guias de backend, FastAPI, OCI e smoke tests estão versionados. A consolidação de entrega e apresentação permanece para a #120. |

## Backend

O backend Spring Boot disponibiliza a API pública sob `/api/v1`. As análises recebem validação de entrada, cálculo de custo estimado, tratamento de erros e persistência.

Na classificação, o backend tenta a API FastAPI interna. Uma resposta aceita produz `fonte_classificacao = ML_MODEL`; indisponibilidade, timeout, erro HTTP ou resposta incompatível acionam a classificação e as recomendações locais, com `fonte_classificacao = RULE_BASED_FALLBACK`.

A autenticação usa JWT para os endpoints protegidos e inclui login, refresh token com rotação, logout e proteção CSRF nos fluxos baseados em cookie. A criação associa a análise ao usuário autenticado; histórico, detalhe e resumo/dashboard são consultados no contexto desse mesmo usuário.

Os detalhes normativos da API pública e da integração interna estão em [docs/api-contract.md](api-contract.md).

## Frontend

O frontend React/Vite está publicado na Vercel em https://energiai.vercel.app, entrega concluída na #119. A configuração de produção, o build, a URL pública e as evidências da publicação estão documentados em [frontend/DEPLOY.md](../frontend/DEPLOY.md). O código possui páginas e serviços para formulário de análise, exibição de resultado, histórico paginado, detalhe e painel de resumo. O cliente HTTP usa a URL configurada por `VITE_API_BASE_URL`, com estados de carregamento e erro nas telas que consomem a API.

Ainda não há, no código atual, integração completa de cadastro, login, refresh, logout, rotas protegidas, envio de cookies de sessão ou tratamento de CSRF. Essa frente permanece pendente na [#118](https://github.com/No-Country-simulation/g9-br-team-09/issues/118).

## Data Science / FastAPI

A API FastAPI é interna e consumida exclusivamente pelo Spring Boot; o frontend não consome `/predict`. A estrutura atual inclui inicialização do serviço de inferência, `GET /health`, `POST /predict`, schemas das cinco features, resposta de inferência com categoria, probabilidade, score, recomendações e `modelo_versao`, além de testes de contrato.

A infraestrutura de integração não define o modelo V2 oficial. Permanecem em desenvolvimento na [#86](https://github.com/No-Country-simulation/g9-br-team-09/issues/86) a seleção do modelo, hiperparâmetros, métricas, holdout, decisão de calibração, artefato serializado e versão concreta final. O deploy da FastAPI está pendente na [#111](https://github.com/No-Country-simulation/g9-br-team-09/issues/111); não há evidência versionada de publicação dela no ambiente de demonstração.

## Cloud / OCI

O repositório possui configuração e procedimentos versionados para implantação e operação do backend em OCI Compute, com Docker Compose, Oracle Autonomous Database como dependência de persistência e readiness, e Caddy como proxy reverso HTTPS. A documentação também registra o fluxo de deploy, rollback e validação por smoke tests autenticados.

O backend é exposto ao Caddy pelo loopback da instância; o Compose não publica a FastAPI. Os smoke tests verificam o contrato público, health, liveness, readiness, autenticação, criação e consulta de análises no ambiente implantado, sem chamar a FastAPI diretamente.

Referências operacionais: [implantação OCI](../infra/deploy/oci/README.md) e [smoke tests](../infra/tests/smoke/README.md).

## Arquitetura atual

A arquitetura de integração presente no repositório é:

~~~text
Frontend React/Vite
        |
        | API pública /api/v1
        v
Spring Boot
   ├── autenticação e autorização
   ├── validação e custo estimado
   ├── persistência e consultas por usuário
   ├── cliente de ML
   └── fallback local
        |
        | API interna /predict
        v
FastAPI
        |
        v
Modelo de Machine Learning

Spring Boot
        |
        v
Oracle Autonomous Database
~~~

Essa integração não significa que o modelo V2 oficial esteja definido nem que a FastAPI esteja implantada no ambiente de demonstração. O Spring Boot permanece o limite público do produto e também responde pelo fallback.

## Persistência

As análises são persistidas pelo backend e vinculadas ao usuário autenticado. A listagem, o detalhe e o resumo/dashboard usam esse vínculo para restringir os dados ao usuário atual. No profile OCI, a aplicação usa Oracle Autonomous Database, com Flyway para migrations e HikariCP para o pool de conexões.

## Pendências principais

- [#86](https://github.com/No-Country-simulation/g9-br-team-09/issues/86): concluir dataset sintético V2 e pipeline de modelagem, sem antecipar o modelo oficial.
- [#118](https://github.com/No-Country-simulation/g9-br-team-09/issues/118): integrar autenticação e sessão no frontend.
- [#111](https://github.com/No-Country-simulation/g9-br-team-09/issues/111): publicar a FastAPI no Render.
- [#121](https://github.com/No-Country-simulation/g9-br-team-09/issues/121): validar o fluxo ponta a ponta Frontend → Backend → FastAPI → Oracle.
- [#120](https://github.com/No-Country-simulation/g9-br-team-09/issues/120): consolidar documentação, evidências e apresentação final.

## Riscos atuais

| Risco | Impacto | Mitigação |
| --- | --- | --- |
| A modelagem V2 atrasar a disponibilização do modelo oficial | Pode adiar a validação com `ML_MODEL` no ambiente final | Concluir a #86 preservando o contrato HTTP já consolidado. |
| A FastAPI permanecer sem deploy | Impede a validação integrada da inferência fora do ambiente local | Publicar a API pela #111 e usar o contrato interno como referência. |
| A autenticação não chegar ao frontend | As telas existentes não consumirão de forma integrada os endpoints protegidos | Implementar cadastro, sessão, CSRF e rotas protegidas na #118. |
| Ausência de validação ponta a ponta | Pode ocultar incompatibilidades entre as quatro camadas | Executar a #121 após disponibilizar os componentes dependentes. |
| Documentação divergir do código | Pode gerar instruções ou status incorretos | Revisar documentação e contratos junto das alterações de cada frente. |

## Próximos passos

1. Concluir a modelagem V2 na #86.
2. Implementar a autenticação integrada do frontend na #118.
3. Publicar a FastAPI pela #111.
4. Executar a validação ponta a ponta da #121.
5. Consolidar a documentação e a apresentação final na #120.

## Referências verificáveis

- [Contrato de integração de APIs](api-contract.md) — fonte normativa da API pública Spring Boot e da API interna FastAPI; a #70 está concluída.
- [README do backend](../backend/README.md) — execução, segurança, persistência e integração de ML.
- [README da API FastAPI](../data-science/README.API.md) — operação local e limitações do serviço interno.
- [Publicação do frontend](../frontend/DEPLOY.md) — build, Vercel, configuração, URL pública e evidências da publicação.
- [Implantação do backend na OCI](../infra/deploy/oci/README.md) — deploy e operação do backend.
- [Smoke tests do backend na OCI](../infra/tests/smoke/README.md) — validação autenticada do contrato público.
