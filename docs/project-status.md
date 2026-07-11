# Status do Projeto — EnergIAI

## Visão geral

O EnergIAI é o projeto da equipe G9-BR-Team 09 para o desafio de inteligência aplicada ao consumo energético.

O MVP tem como objetivo analisar dados de consumo elétrico, classificar o perfil energético em `Eficiente`, `Moderado` ou `Ineficiente`, gerar recomendações de melhoria, estimar custo mensal com base na tarifa de referência de R$ 0,75/kWh e retornar os resultados por API REST.

## Requisitos principais do MVP

- Receber dados de consumo energético.
- Classificar o perfil energético.
- Gerar recomendações de melhoria.
- Calcular custo estimado mensal.
- Retornar resposta em JSON.
- Disponibilizar endpoint mínimo `POST /analise-energetica`.
- Documentar API e exemplos de uso.
- Utilizar ao menos um serviço OCI.
- Registrar evidências de entrega no GitHub, documentação e apresentação final.

## Status geral

| Frente                   | Responsáveis principais                | Status       | Observações                                                                                                     |
| ------------------------ | -------------------------------------- | ------------ | --------------------------------------------------------------------------------------------------------------- |
| Backend                  | Gustavo, Lucas, Rafaela, Adriana, Alan | Em andamento | Estrutura Spring Boot, DTOs, validações, tratamento de erros, cálculo de custo e classificação local avançaram. |
| Data Science             | Túlio, Miguel, Fábio                   | Em andamento | Dataset, critérios, modelo e integração com backend ainda precisam ser consolidados e documentados.             |
| Data Analytics / Produto | Fábio                                  | Em andamento | Organização de requisitos, documentação, atas, status do MVP e alinhamento entre frentes.                       |
| Cloud / OCI              | A validar                              | Pendente     | Serviço definido: Oracle Autonomous Database. Ainda precisa de comprovação técnica e documentação.              |
| Documentação             | Fábio, com revisão do time             | Em andamento | Esta branch organiza documentação geral, decisões, status e contrato da API.                                    |

## Decisões registradas

- Documentação em PT-BR.
- JSON externo em PT-BR.
- Campos do JSON em `snake_case`.
- Código interno em inglês.
- Tarifa de referência: R$ 0,75/kWh.
- Serviço OCI principal: Oracle Autonomous Database.
- Banco local/de testes: H2.
- Backend em Java/Spring Boot.
- Endpoint mínimo obrigatório: `POST /analise-energetica`.
- Python/Data Science será fonte principal para classificação e recomendações, com fallback local no backend quando necessário.

## Pendências principais

- Confirmar contrato final entre backend e Data Science.
- Confirmar implementação do endpoint `POST /analise-energetica`.
- Consolidar dataset e justificativa dos dados.
- Criar ou registrar notebook de EDA.
- Definir modelo simples e critérios de classificação.
- Documentar recomendações geradas.
- Documentar uso de OCI.
- Documentar exemplos de request e response.
- Atualizar README com links para documentação principal.
- Preparar evidências para entrega final e vídeo demo.

## Riscos atuais

| Risco                                        | Impacto                                | Mitigação                                                          |
| -------------------------------------------- | -------------------------------------- | ------------------------------------------------------------------ |
| Escopo de Data Science ficar complexo demais | Pode atrasar integração e MVP          | Priorizar dataset simples, modelo explicável e documentação clara. |
| Endpoint obrigatório não ficar explícito     | Pode comprometer avaliação             | Garantir documentação e teste do `POST /analise-energetica`.       |
| OCI não ser comprovada                       | Pode comprometer requisito obrigatório | Registrar serviço usado, configuração e evidência em documentação. |
| Documentação ficar desalinhada do código     | Pode gerar confusão na entrega         | Revisar documentação via PR antes de merge.                        |
| Alterações diretas em branches protegidas    | Pode quebrar fluxo do time             | Usar branch própria, PR para `develop` e revisão antes de merge.   |

## Próximos passos

1. Atualizar documentação de decisões arquiteturais.
2. Registrar ata da reunião de 09/07.
3. Documentar contrato inicial da API.
4. Validar responsabilidades com backend e Data Science.
5. Abrir PR para revisão do time antes de merge.

## Evidências esperadas

- Issue relacionada à documentação.
- Branch específica para documentação.
- Commits com padrão do repositório.
- Pull Request para `develop`.
- Revisão do time antes de merge.
- Prints ou links das validações principais.
