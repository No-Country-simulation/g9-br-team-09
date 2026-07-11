# Reuniões e Atas — EnergIAI

Este documento registra as principais reuniões, alinhamentos e decisões da equipe G9-BR-Team 09 durante o desenvolvimento do projeto EnergIAI.

## 09/07/2026 — Reunião de alinhamento da equipe

### Contexto

Reunião realizada para alinhar organização do projeto, responsabilidades iniciais, decisões técnicas e próximos passos do MVP.

### Participantes

- Fábio
- Alan
- Adriana
- Gustavo
- Lucas
- Rafaela
- Túlio
- Miguel

### Decisões registradas

- Fábio foi escolhido como representante/líder da equipe por unanimidade.
- A documentação do projeto será feita em PT-BR.
- O contrato externo da API será em PT-BR.
- Os campos do JSON seguirão o padrão `snake_case`.
- O código interno seguirá nomes em inglês.
- Backend será desenvolvido em Java/Spring Boot.
- Decisão inicial: o endpoint mínimo obrigatório do MVP seria a análise energética sem o prefixo de versionamento consolidado posteriormente.
- A tarifa de referência para cálculo de custo será R$ 0,75/kWh.
- O serviço OCI principal definido foi Oracle Autonomous Database.
- O banco local/de testes definido foi H2.
- A frente de Data Science será conduzida principalmente por Túlio, com apoio de Miguel e Fábio.
- A frente de backend será conduzida por Gustavo, Lucas, Rafaela, Adriana e Alan.
- A documentação, requisitos, status do MVP, atas e organização de evidências ficarão sob responsabilidade principal de Fábio.

### Consolidação posterior do contrato público

- O contrato público implementado no backend foi consolidado como `POST /api/v1/analise-energetica`.
- O prefixo `/api/v1` faz parte do contrato público porque está centralizado no `server.servlet.context-path`.
- Os exemplos externos do contrato usam `snake_case`, enums em caixa alta e incluem `score` e `fonte_classificacao` na resposta oficial.

### Alinhamento Backend e Data Science

Fluxo definido até o momento:

- Decisão inicial:
  - Python/Data Science seria a fonte principal para classificação energética e recomendações.
  - Backend seria responsável por validação da entrada, cálculo de custo estimado, orquestração, persistência e retorno da API.
  - Backend também deveria possuir fallback local caso a API Python estivesse indisponível ou retornasse resposta inválida.
- Consolidação posterior:
  - No estado atual do backend, a classificação é executada localmente com `RULE_BASED`.
  - A integração com Data Science permanece como arquitetura-alvo.
  - A responsabilidade final pela geração de recomendações na integração futura ainda depende do contrato definido entre as frentes.

### Pendências da reunião

- Confirmar contrato final entre backend e Data Science.
- Consolidar dataset inicial.
- Registrar critérios de classificação energética.
- Definir modelo simples para o MVP.
- Implementar e documentar a integração HTTP com Data Science.
- Registrar evidência de uso do Oracle Autonomous Database.
- Abrir Pull Request para revisão da documentação antes de merge.

### Observações

Esta ata registra o entendimento inicial da reunião e deve ser revisada pela equipe via Pull Request antes de ser considerada definitiva.
