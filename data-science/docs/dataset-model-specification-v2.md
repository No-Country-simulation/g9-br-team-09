# Especificação V2 — Dataset e Modelo EnergIAI

## 1. Conclusão

A nova versão deverá produzir um **dataset sintético com 5.000 registros**, compatível com o contrato atual do backend, acompanhado de análise científica robusta, modelo supervisionado, probabilidades calibradas, score de risco, recomendações explicáveis e artefatos preparados para integração com FastAPI.

O pipeline de produção receberá exclusivamente:

* `consumo_kwh`;
* `uso_horario_pico`;
* `quantidade_equipamentos`;
* `tipo_imovel`;
* `horas_alto_consumo`.

Esses campos correspondem ao contrato público implementado e aos DTOs atuais do backend.

Nenhum arquivo será criado e nenhuma alteração será feita no GitHub antes da confirmação desta especificação.

---

## 2. Decisões confirmadas na entrevista

| Nº | Decisão                  | Definição aprovada                                                        |
| -- | ------------------------ | ------------------------------------------------------------------------- |
| 1  | Prioridade               | Integração com avaliação científica robusta                               |
| 2  | Tipos de imóvel          | `CASA`, `APARTAMENTO`, `COMERCIO`, `ESCRITORIO`, `INDUSTRIA` e `OUTRO`                                        |
| 3  | Quantidade de registros  | 5.000                                                                     |
| 4  | Distribuição das classes | 30% `EFICIENTE`, 40% `MODERADO`, 30% `INEFICIENTE`                        |
| 5  | Geração do target        | Score explicável com relações não lineares, interações e ruído controlado |
| 6  | Significado do score     | Score alto representa maior risco de ineficiência                         |
| 7  | Score de inferência      | Derivado das probabilidades calibradas                                    |
| 8  | Limites das categorias   | 0–30, 31–60 e 61–100                                                      |
| 9  | Ruído de fronteira       | Aproximadamente 3% dos casos próximos aos limites                         |
| 10 | Casos raros ou extremos  | Aproximadamente 5%                                                        |
| 11 | Dependência de features  | Modelos individuais, ablação e `permutation importance`                   |
| 12 | Variáveis adicionais     | Somente como variáveis latentes do gerador e da auditoria                 |
| 13 | Geração de consumo       | Composição multivariada                                                   |
| 14 | Representação do imóvel  | Enums em caixa alta iguais aos do backend                                 |
| 15 | Valores ausentes         | Nenhum valor ausente nas cinco features                                   |
| 16 | Outliers                 | Aproximadamente 3% de casos plausíveis e identificados                    |
| 17 | Divisão dos dados        | Treino, validação e teste estratificados                                  |
| 18 | Métrica principal        | F1-macro                                                                  |
| 19 | Algoritmos               | Dummy, Regressão Logística, Árvore, Random Forest e HistGradientBoosting  |
| 20 | Hiperparâmetros          | `RandomizedSearchCV` controlado                                           |
| 21 | Validação cruzada        | `RepeatedStratifiedKFold`, 5 folds e 3 repetições                         |
| 22 | Probabilidades           | Comparar original, `sigmoid` e `isotonic`                                 |
| 23 | Importância das features | Diagnóstico combinado                                                     |
| 24 | Ensemble                 | Testar `soft voting` apenas se houver ganho estável                       |
| 25 | Não supervisionado       | Clustering e PCA somente na EDA                                           |
| 26 | Regressão                | Somente diagnóstico auxiliar                                              |
| 27 | Recomendações            | Motor de regras explicável e priorizado                                   |
| 28 | Robustez                 | Teste convencional, fronteiras e mudança controlada de distribuição       |
| 29 | Auditoria humana         | 60 registros estratificados                                               |
| 30 | Publicação               | PR e carregamento por tag ou commit imutável                              |

---

## 3. Decisões herdadas das fontes do projeto

### Contrato público

O endpoint público vigente é:

```http
POST /api/v1/analise-energetica
```

O backend recebe os cinco campos definidos, calcula o custo, persiste a análise e monta a resposta pública.

### Contrato interno planejado

A integração Backend → Data Science deverá utilizar:

```http
POST /predict
```

A FastAPI deverá retornar:

* `categoria`;
* `probabilidade`;
* `score`;
* `recomendacoes`;
* `modelo_versao`.

A FastAPI não calculará custo, não persistirá análises e não implementará o fallback do backend.

### Responsabilidades

**Data Science:**

* classificação principal;
* probabilidade;
* score;
* recomendações;
* versão do modelo.

**Backend:**

* validação pública;
* orquestração;
* custo mensal;
* persistência;
* resposta pública;
* fallback local.

O custo continuará sendo calculado por:

```text
custo_estimado_mensal = consumo_kwh × 0,75
```

### Estado atual

* O notebook inicial já foi versionado e preservado como protótipo histórico.
* A issue do notebook inicial, nº 62, está concluída.
* A FastAPI, o client HTTP, o fallback, a documentação do contrato interno e os testes de integração continuam pendentes.

O contrato público aceita os seis valores de `tipo_imovel`. No classificador local atual, somente `CASA` e `COMERCIO` recebem pontuação específica por tipo; os demais valores continuam válidos, mas não alteram diretamente o score por essa feature. O modelo de Data Science deverá treinar e avaliar os seis tipos, e a divergência entre `ML_MODEL` e `RULE_BASED_FALLBACK` deverá ser medida e documentada antes da integração.

A semântica de `probabilidade` dependerá temporariamente da fonte da classificação:

* `ML_MODEL`: probabilidade calibrada da categoria prevista;
* `RULE_BASED` e `RULE_BASED_FALLBACK`: o valor atual `score / 100` será tratado como confiança heurística, e não como probabilidade calibrada.

Essa diferença deverá permanecer explícita por meio de `fonte_classificacao` até eventual unificação da semântica no backend.

O enunciado exige EDA, treinamento supervisionado, métricas, recomendações, serialização, API REST e uso comprovado de OCI.

---

## 4. Schema proposto

### Dataset oficial de treinamento

| Campo                     | Tipo          | Uso                                           |
| ------------------------- | ------------- | --------------------------------------------- |
| `consumo_kwh`             | `float`       | Feature de produção                           |
| `uso_horario_pico`        | `bool`        | Feature de produção                           |
| `quantidade_equipamentos` | `int`         | Feature de produção                           |
| `tipo_imovel`             | `string enum` | Feature de produção                           |
| `horas_alto_consumo`      | `int`         | Feature de produção                           |
| `categoria`               | `string enum` | Target supervisionado                         |
| `score_referencia`        | `int`         | Auditoria da geração; proibido como feature   |
| `tipo_cenario`            | `string`      | Auditoria: típico, fronteira, raro ou extremo |
| `caso_fronteira`          | `bool`        | Auditoria                                     |
| `caso_raro`               | `bool`        | Auditoria                                     |
| `outlier_plausivel`       | `bool`        | Auditoria                                     |
| `lote_geracao`            | `string`      | Rastreabilidade do gerador                    |

### Valores permitidos

```text
tipo_imovel:
- CASA
- APARTAMENTO
- COMERCIO
- ESCRITORIO
- INDUSTRIA
- OUTRO

categoria:
- EFICIENTE
- MODERADO
- INEFICIENTE
```

### Campos proibidos como features

* `categoria`;
* `score_referencia`;
* recomendações;
* probabilidade;
* custo estimado;
* identificadores;
* flags de auditoria;
* variáveis latentes;
* qualquer informação calculada diretamente do target.

O pipeline deverá selecionar explicitamente apenas as cinco features de produção.

---

## 5. Geração sintética

### Arquitetura do gerador

1. Definir matriz de cenários.
2. Gerar variáveis latentes internas.
3. Gerar as cinco features observáveis.
4. Calcular score de referência apenas com as features observáveis.
5. Aplicar interações e relações não lineares.
6. Aplicar pequeno ruído apenas próximo às fronteiras.
7. Converter o score de referência em categoria.
8. Validar cada registro.
9. Rejeitar ou reparar somente registros inválidos.
10. Deduplicar e auditar a base final.

### Relações esperadas

O consumo deverá variar conforme:

* tipo de imóvel;
* quantidade de equipamentos;
* horas de alto consumo;
* uso em horário de pico;
* interações entre essas variáveis;
* ruído controlado.

Nenhuma variável isolada deverá determinar praticamente toda a categoria.

### Limites do target

```text
0 a 30   → EFICIENTE
31 a 60  → MODERADO
61 a 100 → INEFICIENTE
```

---

## 6. Hipóteses operacionais propostas

Estas definições não foram perguntadas separadamente e serão consideradas aprovadas com a confirmação da especificação:

### Distribuição dos imóveis

* 32% `CASA`;
* 32% `APARTAMENTO`;
* 16% `COMERCIO`;
* 10% `ESCRITORIO`;
* 5% `INDUSTRIA`;
* 5% `OUTRO`.

As proporções representam uma distribuição sintética de projeto e poderão variar ligeiramente para atender às quotas por classe e cenário.

Para manter aderência ao escopo do desafio, `INDUSTRIA` representará pequenas unidades produtivas ou oficinas, enquanto `OUTRO` ficará reservado a imóveis de pequeno porte que não se enquadrem nas demais categorias.

### Faixas sintéticas das features

As faixas abaixo são premissas controladas do gerador sintético e não alteram as regras de validação pública do backend.

No contrato público atual:

* `consumo_kwh` deve ser maior que zero;
* `quantidade_equipamentos` deve ser maior ou igual a 1;
* `horas_alto_consumo` deve estar entre 0 e 24.

#### Limites absolutos do gerador

| Campo                     | Mínimo | Máximo |
| ------------------------- | -----: | -----: |
| `consumo_kwh`             |     60 |  2.500 |
| `quantidade_equipamentos` |      1 |     60 |
| `horas_alto_consumo`      |      0 |     24 |

#### Faixas típicas por tipo de imóvel

| Tipo          | `consumo_kwh` | Equipamentos | Horas de alto consumo |
| ------------- | ------------: | -----------: | --------------------: |
| `CASA`        |       180–520 |         4–22 |                  1–12 |
| `APARTAMENTO` |       140–390 |         3–18 |                  1–11 |
| `COMERCIO`    |       240–560 |         5–28 |                  2–14 |
| `ESCRITORIO`  |       180–700 |         5–35 |                  3–14 |
| `INDUSTRIA`   |     300–1.400 |         8–50 |                  4–20 |
| `OUTRO`       |       120–800 |         2–30 |                  1–16 |

Essas faixas não representam estatísticas do mercado real. Elas serão utilizadas para criar cenários sintéticos coerentes, variados e reproduzíveis.

#### Critérios dos casos especiais

* caso típico: todas as variáveis dentro das faixas típicas do respectivo tipo de imóvel;
* caso raro ou extremo: pelo menos uma variável fora da faixa típica, mas dentro dos limites absolutos;
* outlier plausível: caso raro identificado por IQR ou escore robusto, ainda dentro dos limites absolutos e sem combinação incoerente;
* os outliers representarão aproximadamente 3% da base e serão subconjunto dos aproximadamente 5% de casos raros ou extremos;
* nenhum valor inválido será gerado apenas para testar erros da API.

O campo `uso_horario_pico` será gerado como variável booleana conforme os cenários, as interações entre features e as quotas das categorias.

### Divisão dos dados

* 70% treino;
* 15% validação;
* 15% teste;
* estratificação por categoria.

### Reprodutibilidade

```python
RANDOM_SEED = 42
```

A seed deverá ser registrada nos metadados.

### Cálculo da categoria, probabilidade e score de inferência

No fluxo `ML_MODEL`, a categoria e a probabilidade serão obtidas diretamente das probabilidades calibradas do classificador:

```text
categoria = argmax(P(EFICIENTE), P(MODERADO), P(INEFICIENTE))
probabilidade = max(P(EFICIENTE), P(MODERADO), P(INEFICIENTE))
```

O score permanecerá como indicador contínuo de severidade esperada:

```text
score = round(
    0 × P(EFICIENTE)
    + 50 × P(MODERADO)
    + 100 × P(INEFICIENTE)
)
```

O score deverá permanecer entre 0 e 100 e não substituirá a categoria prevista pelo modelo.

As faixas 0–30, 31–60 e 61–100 serão utilizadas somente para interpretação da severidade do score, e não para recalcular ou sobrescrever a categoria.

---

## 7. Modelagem

### Pipeline

* `ColumnTransformer`;
* `OneHotEncoder(handle_unknown="ignore")`;
* tratamento explícito das variáveis numéricas;
* `Pipeline` do Scikit-learn;
* seed fixa;
* nenhuma transformação treinada com o conjunto de teste.

### Algoritmos

1. `DummyClassifier`;
2. Regressão Logística;
3. Árvore de Decisão;
4. Random Forest;
5. `HistGradientBoostingClassifier`.

### Ajuste

* `RandomizedSearchCV`;
* espaço de busca reduzido e documentado;
* validação cruzada estratificada;
* seleção sem consultar o conjunto de teste.

### Métricas

* acurácia;
* balanced accuracy;
* precisão por classe;
* recall por classe;
* F1 por classe;
* F1-macro;
* F1-weighted;
* matriz de confusão;
* log loss;
* média e desvio da validação cruzada;
* tempo aproximado de inferência;
* avaliação de calibração.

A descrição obrigatória dos resultados será:

> O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.

Nenhuma conclusão será apresentada como desempenho em dados reais.

---

## 8. Validação da contribuição multivariada

Serão executados:

1. correlação e associação;
2. mutual information;
3. modelos treinados com uma feature por vez;
4. permutation importance;
5. remoção de uma feature por vez;
6. comparação do modelo completo com os modelos reduzidos;
7. análise por tipo de imóvel;
8. análise das interações;
9. inspeção dos casos de fronteira.

### Guardrail inicial

O melhor modelo com uma única feature não deverá alcançar mais de 95% do F1-macro obtido pelo modelo completo.

Caso isso ocorra:

1. registrar o resultado;
2. revisar a fórmula sintética;
3. reduzir a dependência direta;
4. aumentar interações justificáveis;
5. gerar novamente a base;
6. repetir os testes.

Não serão manipuladas artificialmente as importâncias para que todas fiquem iguais.

---

## 9. Calibração e score

Serão comparados:

* modelo original;
* calibração `sigmoid`;
* calibração `isotonic`, quando houver amostras suficientes.

A escolha considerará:

* log loss;
* comportamento da curva de calibração;
* estabilidade entre divisões;
* manutenção do F1-macro;
* coerência entre categoria, probabilidade e score.

A calibração somente será adotada quando melhorar a qualidade probabilística ou apresentar justificativa técnica clara.

---

## 10. Recomendações

O motor de regras utilizará apenas:

* cinco features de entrada;
* categoria prevista;
* score previsto.

Cada regra deverá possuir internamente:

* código;
* condição;
* prioridade;
* mensagem;
* justificativa;
* variáveis acionadoras.

### Requisitos

* lista nunca vazia;
* mensagens sem contradição;
* mensagens sem duplicidade;
* ordem por prioridade;
* limite máximo definido;
* recomendação geral de fallback;
* testes unitários.

O retorno da FastAPI continuará sendo apenas uma lista de textos.

---

## 11. EDA e análises complementares

### EDA obrigatória

* distribuições numéricas;
* frequência das categorias;
* frequência por tipo de imóvel;
* relações entre features;
* análise de fronteiras;
* análise de outliers;
* análise por cenário;
* verificação das quotas;
* comparação entre splits.

### Não supervisionado

* K-Means;
* avaliação por silhouette e elbow;
* PCA somente para visualização;
* agrupamento hierárquico opcional em amostra.

Clusters não serão usados como target ou campo da API.

### Regressão

Será usada apenas como diagnóstico de coerência do consumo ou score de referência.

Não será usada para calcular custo.

---

## 12. Testes de robustez

Serão avaliados:

* conjunto de teste isolado;
* registros próximos dos limites 30/31 e 60/61;
* cenários extremos plausíveis;
* alterações controladas nas distribuições;
* categorias por tipo de imóvel;
* os seis valores válidos de `tipo_imovel` presentes nos conjuntos de treino, validação e teste;
* valores fora do enum rejeitados pela validação pública da API;
* `OneHotEncoder(handle_unknown="ignore")` mantido somente como proteção interna do pipeline;
* estabilidade entre seeds selecionadas.

A auditoria humana utilizará 60 registros distribuídos por:

* categoria;
* tipo de imóvel;
* fronteiras;
* outliers;
* cenários raros;
* casos típicos.

---

## 13. Artefatos previstos

```text
data-science/
├── data/
│   ├── dataset_energiai_v2.csv
│   └── dataset_energiai_v2.metadata.json
├── notebooks/
│   ├── prototipo_base_sintetica.ipynb
│   └── 01_dataset_modelagem_energiai_v2.ipynb
├── models/
│   ├── modelo_energetico_v2.joblib
│   └── modelo_energetico_v2.metadata.json
├── examples/
│   └── exemplos_predict_v2.json
├── tests/
│   ├── test_dataset_quality.py
│   ├── test_recommendations.py
│   └── test_model_contract.py
├── requirements.txt
└── README.md
```

O notebook atual será preservado sem reescrita como protótipo histórico.

---

## 14. Metadados obrigatórios

* versão do dataset;
* data de geração;
* seed;
* quantidade de registros;
* schema;
* distribuição das classes;
* distribuição dos imóveis;
* fórmulas;
* limites;
* número de reparos e rejeições;
* hash SHA-256 do CSV;
* algoritmo selecionado;
* hiperparâmetros;
* métricas;
* features;
* classes;
* versões do Python e bibliotecas;
* hash do modelo;
* limitações;
* commit ou tag de origem.

---

## 15. Publicação e carregamento

Nenhuma ação será executada sem instrução explícita.

Quando autorizado:

1. abrir issue;
2. criar branch a partir de `develop`;
3. criar os arquivos;
4. executar notebook e testes;
5. revisar `git status --short`;
6. revisar `git diff`;
7. criar commit pequeno;
8. fazer push;
9. abrir PR para `develop`;
10. solicitar revisão;
11. aguardar aprovação;
12. não fazer merge sem autorização.

Depois do merge, o notebook deverá carregar o CSV por tag ou commit imutável:

```python
DATASET_URL = (
    "https://raw.githubusercontent.com/"
    "No-Country-simulation/g9-br-team-09/"
    "<TAG_OU_COMMIT>/data-science/data/dataset_energiai_v2.csv"
)
```

O carregamento deverá conferir:

* disponibilidade do arquivo;
* hash SHA-256;
* versão;
* schema;
* quantidade de colunas;
* tipos e domínios.

---

## 16. Principais riscos

| Risco                               | Mitigação                                                              |
| ----------------------------------- | ---------------------------------------------------------------------- |
| Dataset excessivamente artificial   | Ruído controlado, cenários variados, auditoria e limitações explícitas |
| Uma feature dominar o target        | Modelos individuais, ablação e revisão do gerador                      |
| Vazamento do target                 | Lista explícita das cinco features e testes do pipeline                |
| Probabilidade não confiável         | Avaliação e calibração                                                 |
| Divergência entre score e categoria | Regra única, testes de contrato e casos de fronteira                   |
| Divergência entre modelo e fallback | Limites iguais e contrato documentado                                  |
| Escopo excessivo                    | Clustering e regressão somente como análises auxiliares                |
| Integração atrasar                  | Artefatos e contrato definidos antes da FastAPI                        |
| OCI ser declarada sem evidência     | Não relacionar este trabalho a OCI concluída sem prova técnica         |
| Dataset mudar silenciosamente       | Hash, metadados e referência imutável                                  |

---

## 17. Critérios de aceite

### Dataset

* exatamente 5.000 registros válidos;
* nenhuma feature obrigatória nula;
* nenhuma duplicata integral;
* categorias válidas;
* tipos de imóvel válidos;
* intervalos respeitados;
* distribuição próxima de 30% / 40% / 30%;
* cenários e lotes rastreáveis;
* relatório de qualidade aprovado.

### Modelo

* superar claramente o `DummyClassifier`;
* avaliação por F1-macro;
* teste final isolado;
* probabilidades avaliadas;
* score entre 0 e 100;
* categorias coerentes com os limites;
* inferência reproduzível;
* pipeline serializado;
* nenhuma variável proibida no pipeline.

### Recomendações

* nenhuma resposta vazia;
* ausência de contradições;
* regras priorizadas;
* testes unitários aprovados.

### Integração

Request:

```json
{
  "consumo_kwh": 420.0,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 10,
  "tipo_imovel": "CASA",
  "horas_alto_consumo": 8
}
```

Response:

```json
{
  "categoria": "INEFICIENTE",
  "probabilidade": 0.81,
  "score": 81,
  "recomendacoes": [
    "Reduzir o uso de equipamentos durante horários de pico"
  ],
  "modelo_versao": "energy-classifier-v2"
}
```

### Documentação

* notebook executável do início ao fim;
* Markdown antes das células relevantes;
* premissas e limitações documentadas;
* exemplos JSON;
* metadados completos;
* instruções para Colab, Jupyter e VS Code;
* nenhuma alegação de desempenho em dados reais;
* nenhuma alegação de integração ou OCI concluída sem evidência.

---

## 18. Plano de execução após confirmação

1. Criar a matriz de atributos e quotas.
2. Definir o schema programático.
3. Criar os exemplos de referência.
4. Implementar o gerador sintético.
5. Validar uma amostra pequena.
6. Gerar os 5.000 registros.
7. Executar relatório de qualidade.
8. Realizar EDA.
9. Validar dependência multivariada.
10. Treinar e comparar modelos.
11. Ajustar hiperparâmetros.
12. Avaliar calibração.
13. Testar ensemble, se justificado.
14. Executar robustez e auditoria humana.
15. Criar motor de recomendações.
16. Serializar o pipeline.
17. Validar o contrato da FastAPI.
18. Preparar arquivos para futura publicação.
