# Revisão técnica da geração do target — Dataset EnergiAI V2

## Status

> **Registro histórico de proposta.** Esta revisão foi elaborada durante o
> Draft PR #122, antes da conclusão da Issue #86. Não representa decisão atual
> nem instrução operacional. A solução final e os resultados da proposta estão
> consolidados no [relatório final de modelagem V2](modeling-final-report-v2.md).

## Contexto

A implementação atual da issue #86 seguiu a arquitetura definida na
Especificação V2:

1. geração das cinco features observáveis;
2. cálculo determinístico do `score_referencia` com essas features;
3. conversão direta do score em `categoria`;
4. recálculo de score e categoria após mutações de casos raros e outliers.

O código e os testes confirmam que a implementação está alinhada ao contrato
aprovado. A revisão técnica identificou, porém, que esse mecanismo permite
reconstruir o target a partir das mesmas entradas utilizadas pelo modelo.

Essa característica reduz o valor do treinamento como demonstração de
aprendizado de relações sintéticas não triviais.

## Diagnóstico confirmado

O fluxo atual é:

```text
cinco features observáveis
        ↓
score_referencia determinístico
        ↓
categoria por faixas fixas
```

Consequências:

- a categoria pode ser recuperada pela regra do gerador;
- casos raros e outliers alteram as features, mas depois recalculam score e
  categoria pela mesma regra;
- a variedade das entradas aumenta, porém o acoplamento permanece;
- as métricas mediriam principalmente a reprodução da regra sintética;
- nenhuma métrica poderá ser apresentada como desempenho em dados reais.

## Contratos que devem ser preservados

A revisão proposta deverá preservar:

- exatamente cinco features de produção:
  - `consumo_kwh`;
  - `uso_horario_pico`;
  - `quantidade_equipamentos`;
  - `tipo_imovel`;
  - `horas_alto_consumo`;
- target `categoria`;
- classes `EFICIENTE`, `MODERADO` e `INEFICIENTE`;
- distribuição aproximada `30% / 40% / 30%`;
- seed padrão `42`;
- limites físicos definidos no schema;
- cerca de 3% de registros de fronteira;
- cerca de 5% de casos raros ou extremos;
- cerca de 3% de outliers plausíveis como subconjunto dos casos raros;
- campos de auditoria fora das features;
- contrato externo do backend e da FastAPI;
- categoria de inferência obtida por `argmax`;
- score de inferência como severidade, sem sobrescrever a categoria.

## Arquitetura experimental proposta

O fluxo experimental proposto é:

```text
matriz de cenários e tipo de imóvel
        ↓
estado latente de eficiência
        ↓
score_referencia de auditoria
        ↓
categoria sintética
        ↓
cinco features observáveis geradas probabilisticamente
```

Nesse desenho:

- o estado latente será exclusivamente interno ao gerador;
- o estado latente não será persistido no dataset oficial;
- o estado latente não será utilizado como feature;
- o `score_referencia` continuará proibido como feature;
- as faixas `0–30`, `31–60` e `61–100` poderão continuar orientando a
  categoria sintética;
- as features serão influenciadas probabilisticamente pela severidade latente;
- haverá sobreposição controlada entre classes;
- nenhuma feature deverá determinar praticamente toda a categoria;
- as cinco features deverão preservar sinal suficiente para o MVP.

## Reaproveitamento da implementação atual

Poderão ser preservados:

- schema e enums;
- limites numéricos;
- distribuição dos tipos de imóvel;
- cenários e faixas típicas;
- validações de entrada;
- geração probabilística do uso em horário de pico;
- geração multivariada do consumo;
- ruído controlado;
- flags e quotas de auditoria;
- seleção de casos raros;
- seleção de outliers plausíveis;
- testes de estrutura, limites, tipos e reprodutibilidade.

Deverão ser avaliados ou revisados:

- cálculo determinístico do score pelas cinco features;
- conversão obrigatória do score recalculado em categoria;
- recálculo da categoria depois das mutações;
- testes que exigem igualdade exata entre features, score e target;
- mecanismo de seleção de fronteiras;
- influência latente sobre equipamentos, horas, pico e consumo.

## Estratégia de implementação segura

1. Preservar a baseline determinística atual.
2. Criar funções novas e explícitas para o caminho experimental.
3. Adicionar testes antes de migrar a orquestração final.
4. Comparar baseline e proposta com a mesma seed.
5. Validar o guardrail de dependência das features.
6. Registrar resultados e decisões no Draft PR #122.
7. Atualizar a Especificação V2 somente após decisão registrada.
8. Gerar o CSV oficial somente depois da aprovação técnica.

## Validações obrigatórias

A proposta deverá comprovar:

- reprodutibilidade;
- ausência de valores inválidos ou ausentes;
- respeito aos limites físicos;
- distribuição aproximada das três categorias;
- sobreposição observável entre classes;
- consistência das quotas de cenários;
- ausência de campos proibidos no pipeline;
- avaliação de modelos com uma feature por vez;
- baseline Dummy;
- Regressão Logística;
- Árvore de Decisão;
- Random Forest;
- HistGradientBoosting;
- F1-macro;
- ablação;
- `permutation importance`;
- comparação por lote ou cenário;
- melhor modelo individual limitado a, no máximo, 95% do F1-macro do modelo
  completo.

A calibração somente será adotada com evidência técnica.

## Decisões pendentes

Antes da implementação definitiva, a equipe deverá validar:

- forma de geração do estado latente;
- distribuição do score dentro de cada categoria;
- intensidade da influência latente em cada feature;
- magnitude do ruído;
- nível aceitável de sobreposição entre classes;
- tratamento das fronteiras;
- comportamento de casos raros e outliers;
- estratégia de split por lote ou cenário;
- critérios técnicos para aprovação ou rejeição da proposta.

## Limitações

O dataset continuará sendo sintético.

Mesmo com a revisão:

- não haverá comprovação de desempenho em dados reais;
- as relações geradas representarão hipóteses controladas;
- as métricas servirão para comparar abordagens no ambiente sintético;
- as limitações deverão permanecer explícitas na documentação e na
  apresentação.

## Rastreabilidade

- Issue principal: #86
- Draft Pull Request: #122
- Especificação vigente: `dataset-model-specification-v2.md`
- Baseline atual: target determinístico derivado das cinco features
- Estado desta proposta: pendente de validação coletiva
