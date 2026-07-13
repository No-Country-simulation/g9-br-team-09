# Status do Projeto — EnergIAI

## Visão geral

O EnergIAI é o projeto da equipe G9-BR-Team 09 para o desafio de inteligência aplicada ao consumo energético.

O MVP tem como objetivo analisar dados de consumo elétrico, classificar o perfil energético em `EFICIENTE`, `MODERADO` ou `INEFICIENTE`, gerar recomendações de melhoria, estimar custo mensal com base na tarifa de referência de R$ 0,75/kWh e retornar os resultados por API REST.

## Requisitos principais do MVP

- Receber dados de consumo energético.
- Classificar o perfil energético.
- Gerar recomendações de melhoria.
- Calcular custo estimado mensal.
- Retornar resposta em JSON.
- Disponibilizar endpoint público `POST /api/v1/analise-energetica`.
- Documentar API e exemplos de uso.
- Utilizar ao menos um serviço OCI.
- Registrar evidências de entrega no GitHub, documentação e apresentação final.

## Status geral

| Frente                   | Responsáveis principais                | Status       | Observações                                                                                                     |
| ------------------------ | -------------------------------------- | ------------ | --------------------------------------------------------------------------------------------------------------- |
| Backend                  | Gustavo, Lucas, Rafaela, Adriana, Alan | Em andamento | Endpoint público, validações, tratamento de erros, cálculo de custo e classificador local `RULE_BASED` já existem no backend atual. |
| Data Science             | Túlio, Miguel, Fábio                   | Em andamento | Integração HTTP com modelo externo ainda é etapa futura; contrato entre frentes ainda precisa ser consolidado. |
| Data Analytics / Produto | Fábio                                  | Em andamento | Organização de requisitos, documentação, atas e alinhamento entre frentes seguem ativos. |
| Cloud / OCI              | A validar                              | Pendente     | Oracle Autonomous Database foi definido como serviço OCI principal, mas a evidência de uso no projeto ainda precisa ser acompanhada. |
| Documentação             | Fábio, com revisão do time             | Em andamento | Documentação revisada para refletir o contrato público atual e separar estado atual de arquitetura-alvo. |

## Estado atual do backend

- O endpoint público `POST /api/v1/analise-energetica` já foi implementado.
- O contrato externo em `snake_case` já está definido no backend.
- O backend recebe e valida o request, calcula o custo estimado, gera recomendações e monta a resposta final.
- A classificação atual é local, baseada em regras, com `fonte_classificacao = RULE_BASED`.
- Os enums públicos aceitos hoje incluem `tipo_imovel` em caixa alta e `categoria` em caixa alta.
- O formato de erro segue `timestamp`, `status`, `error` e `message`.
- O custo estimado usa tarifa de referência de R$ 0,75/kWh.
- O perfil local/de desenvolvimento usa H2 conforme `application-local.properties`.

## Arquitetura-alvo

- Data Science disponibilizará a classificação por modelo por meio da integração definida pelo time.
- O backend continuará responsável pela API pública, validação, orquestração, cálculo de custo, persistência e resposta final.
- A responsabilidade final sobre recomendações na integração com Data Science ainda deve seguir o contrato entre as frentes.
- Em falha, timeout ou resposta inválida da integração futura, o backend poderá usar `RULE_BASED_FALLBACK`.
- `ML_MODEL` e `RULE_BASED_FALLBACK` são valores previstos no contrato público, mas não representam o estado atual em produção do backend desta branch.

## Pendências principais

- Consolidar o contrato de integração entre backend e Data Science.
- Implementar e validar a integração HTTP com Data Science.
- Consolidar dataset, notebook de EDA e critérios do modelo de ML.
- Definir no contrato de integração a responsabilidade final pela geração de recomendações.
- Acompanhar as issues específicas de persistência.
- Registrar e documentar evidências reais de uso do Oracle Autonomous Database.
- Preparar evidências para entrega final e vídeo demo.

## Riscos atuais

| Risco                                        | Impacto                                | Mitigação                                                          |
| -------------------------------------------- | -------------------------------------- | ------------------------------------------------------------------ |
| Escopo de Data Science ficar complexo demais | Pode atrasar integração e MVP          | Priorizar dataset simples, modelo explicável e documentação clara. |
| Contrato público ficar desalinhado do código | Pode comprometer integração e avaliação | Manter `docs/api-contract.md` como referência principal e revisar contra o backend. |
| OCI não ser comprovada                       | Pode comprometer requisito obrigatório | Registrar serviço usado, configuração e evidência em documentação. |
| Documentação ficar desalinhada do código     | Pode gerar confusão na entrega         | Revisar documentação via PR antes de merge.                        |
| Alterações diretas em branches protegidas    | Pode quebrar fluxo do time             | Usar branch própria, PR para `develop` e revisão antes de merge.   |

## Próximos passos

1. Validar com o time o contrato de integração Backend/Data Science.
2. Implementar a integração HTTP com Data Science.
3. Acompanhar o estado real da persistência e das evidências de Oracle Autonomous Database.
4. Revisar a documentação quando a integração com `ML_MODEL` estiver concluída.
5. Submeter esta atualização documental para revisão antes de merge.

## Evidências esperadas

- Issue relacionada à documentação.
- Branch específica para documentação.
- Commits com padrão do repositório.
- Pull Request para `develop`.
- Revisão do time antes de merge.
- Prints ou links das validações principais.
