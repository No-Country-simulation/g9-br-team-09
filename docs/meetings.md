# Reuniões e Atas — EnergiAI

Este documento registra as principais reuniões, alinhamentos e decisões da equipe G9-BR-Team 09 durante o desenvolvimento do projeto EnergiAI.

## 09/07/2026 — Reunião de alinhamento da equipe

### Contexto da reunião de 09/07

Reunião realizada para alinhar a organização inicial do projeto, responsabilidades da equipe, decisões técnicas, documentação e próximos passos do MVP.

### Participantes da reunião de 09/07

- Fábio
- Alan
- Adriana
- Gustavo
- Lucas
- Rafaela
- Túlio
- Miguel

### Decisões registradas em 09/07

- Fábio foi escolhido como representante/líder da equipe por unanimidade.
- A documentação do projeto será feita em PT-BR.
- O contrato externo da API será em PT-BR.
- Os campos do JSON seguirão o padrão `snake_case`.
- O código interno seguirá nomes em inglês.
- O backend será desenvolvido em Java/Spring Boot.
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

### Alinhamento entre backend e Data Science em 09/07

Fluxo definido até o momento:

- Python/Data Science será a fonte principal para classificação energética e recomendações na arquitetura-alvo.
- Backend será responsável por validação da entrada, cálculo de custo estimado, orquestração, persistência e retorno da API.
- Backend deverá possuir fallback local caso a API Python esteja indisponível ou retorne resposta inválida.
- No estado atual do backend, a classificação é executada localmente com `RULE_BASED`.
- A integração com Data Science permanece como arquitetura-alvo.
- A responsabilidade final pela geração de recomendações na integração futura depende do contrato definido entre as frentes.

### Pendências registradas em 09/07

- Confirmar contrato final entre backend e Data Science.
- Consolidar dataset inicial.
- Registrar critérios de classificação energética.
- Definir modelo simples para o MVP.
- Implementar e documentar a integração HTTP com Data Science.
- Registrar evidência de uso do Oracle Autonomous Database.
- Abrir Pull Request para revisão da documentação antes de merge.

### Observações sobre a reunião de 09/07

Esta ata registra o entendimento inicial da reunião e deve ser revisada pela equipe via Pull Request antes de ser considerada definitiva.

---

## 13/07/2026 — Sprint Meet Semana 1

### Contexto da reunião de 13/07

Reunião realizada para alinhar o andamento da Semana 1 do projeto EnergiAI, revisar o status do backend, identificar pendências de Data Science, discutir próximos passos de OCI e preparar a equipe para a Sprint Demo interna da semana.

### Participantes da reunião de 13/07

- Fábio
- Alan
- Gustavo
- Lucas
- Adriana — via chat

### Alinhamentos registrados em 13/07

- A Semana 0 foi considerada praticamente concluída pela equipe.
- O backend avançou além do esperado para o início da Semana 1.
- A documentação geral do projeto já foi organizada e mergeada em `develop`.
- A migration da tabela `energy_analysis` foi incorporada à base atual do projeto.
- O contrato público da API permanece centralizado em `POST /api/v1/analise-energetica`.
- A integração com Data Science segue como arquitetura-alvo.
- A equipe aguarda o notebook, base ou modelo inicial de Data Science para iniciar testes de integração.
- A API Python foi citada como caminho provável para o backend Java consumir a classificação do modelo.
- As recomendações devem ser tratadas como responsabilidade principal da frente de Data Science na arquitetura-alvo.
- O backend deve manter fallback local para garantir funcionamento do MVP caso a integração externa falhe.
- Oracle Autonomous Database segue como serviço OCI principal previsto, ainda dependendo de validação técnica e evidência real.
- A Sprint Demo de quinta-feira será usada para demonstrar o andamento interno da sprint.
- As entregas da plataforma podem ser atualizadas progressivamente até a Semana 5.
- Foi reforçado que a documentação deve registrar apenas informações confirmadas e não apresentar integrações futuras como concluídas.

### Pendências identificadas em 13/07

- Confirmar entrega do notebook, base ou modelo inicial de Data Science.
- Confirmar atualização da frente de Data Science sobre dataset, EDA, critérios, modelo e métricas.
- Definir o formato final da integração entre backend Java e Data Science.
- Implementar e validar a integração HTTP com a API Python, caso esse caminho seja confirmado.
- Registrar evidência técnica real do uso do Oracle Autonomous Database.
- Definir o que será apresentado na Sprint Demo interna da Semana 1.
- Atualizar entregáveis da plataforma conforme avanço real do projeto.

### Próximos passos definidos em 13/07

- Acompanhar a entrega do notebook de Data Science.
- Manter o acompanhamento dos PRs e issues do backend.
- Validar o estado real da integração com OCI antes de documentar como concluída.
- Preparar um resumo objetivo do andamento para a Sprint Demo.
- Atualizar a documentação apenas com informações confirmadas.
- Manter a transcrição completa apenas como fonte interna de apoio, sem publicá-la no repositório.

### Observações sobre a reunião de 13/07

Esta ata registra apenas os pontos operacionais da reunião. A transcrição completa não deve ser publicada no repositório por conter conversas pessoais e trechos informais que não fazem parte da documentação oficial do projeto.
