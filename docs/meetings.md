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
- O endpoint mínimo obrigatório do MVP será `POST /analise-energetica`.
- A tarifa de referência para cálculo de custo será R$ 0,75/kWh.
- O serviço OCI principal definido foi Oracle Autonomous Database.
- O banco local/de testes definido foi H2.
- A frente de Data Science será conduzida principalmente por Túlio, com apoio de Miguel e Fábio.
- A frente de backend será conduzida por Gustavo, Lucas, Rafaela, Adriana e Alan.
- A documentação, requisitos, status do MVP, atas e organização de evidências ficarão sob responsabilidade principal de Fábio.

### Alinhamento Backend e Data Science

Fluxo definido até o momento:

- Python/Data Science será a fonte principal para classificação energética e recomendações.
- Backend será responsável por validação da entrada, cálculo de custo estimado, orquestração, persistência e retorno da API.
- Backend também deverá possuir fallback local para classificação e recomendações caso a API Python esteja indisponível ou retorne resposta inválida.

### Pendências da reunião

- Confirmar contrato final entre backend e Data Science.
- Consolidar dataset inicial.
- Registrar critérios de classificação energética.
- Definir modelo simples para o MVP.
- Confirmar implementação e documentação do endpoint `POST /analise-energetica`.
- Documentar exemplos de request e response.
- Registrar evidência de uso do Oracle Autonomous Database.
- Atualizar README com links para documentação principal.
- Abrir Pull Request para revisão da documentação antes de merge.

### Observações

Esta ata registra o entendimento inicial da reunião e deve ser revisada pela equipe via Pull Request antes de ser considerada definitiva.
