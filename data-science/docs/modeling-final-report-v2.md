# Relatório final de modelagem — EnergiAI V2

## 1. Conclusão

A modelagem V2 do EnergiAI foi concluída com uma solução supervisionada congelada composta por **Random Forest + calibração isotônica**, utilizando exclusivamente as cinco features públicas previstas no contrato de inferência.

A seleção do modelo, o ajuste de hiperparâmetros e a decisão sobre calibração foram realizados somente com treino e validação. O holdout permaneceu isolado até o congelamento da solução e a validação explícita do Marco 2.

Após essa validação, o holdout oficial foi avaliado exatamente uma vez. Nenhuma alteração de modelo, hiperparâmetros, calibração ou features foi realizada em resposta às métricas finais.

O artefato serializado foi validado, versionado e utilizado com sucesso pela FastAPI existente nos endpoints `/health` e `/predict` em ambiente local.

> **O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.**

Os resultados não comprovam desempenho em produção, comportamento energético real, causalidade ou validade externa.

---

## 2. Escopo

Este relatório consolida as evidências finais da issue #86 relacionadas a:

- dataset sintético V2;
- isolamento metodológico;
- diagnósticos de dependência multivariada;
- comparação dos modelos supervisionados;
- tuning dos finalistas;
- avaliação probabilística e calibração;
- congelamento da solução;
- avaliação oficial única do holdout;
- serialização e rastreabilidade do modelo;
- testes de contrato;
- exemplos de inferência;
- validação local da FastAPI com o artefato final.

Não fazem parte do escopo desta entrega:

- integração completa Spring Boot → FastAPI;
- deploy OCI da FastAPI;
- autenticação;
- frontend;
- E2E da aplicação completa;
- alterações no fallback do backend.

---

## 3. Contrato das features

O pipeline utiliza exclusivamente:

```text
consumo_kwh
uso_horario_pico
quantidade_equipamentos
tipo_imovel
horas_alto_consumo
```

São proibidos como features:

```text
categoria
score_referencia
recomendacoes
probabilidade
custo
IDs
flags de auditoria
variáveis latentes
campos derivados diretamente do target
```

A categoria de inferência é definida pelo `argmax` das probabilidades.

A probabilidade exposta corresponde à maior probabilidade da categoria prevista.

O score é um indicador contínuo de severidade entre 0 e 100 e não sobrescreve a categoria produzida pelo modelo.

---

## 4. Dataset sintético V2

Artefato principal:

```text
data-science/data/dataset_energiai_v2.csv
```

Características finais:

| Critério | Resultado |
| --- | ---: |
| Registros | 5.000 |
| Seed | 42 |
| Features de produção | 5 |
| Tipos de imóvel | 6 |
| Valores nulos | 0 |
| Valores não finitos | 0 |
| Duplicados completos | 0 |
| Duplicados nas cinco features | 0 |

Distribuição das categorias:

| Categoria | Registros |
| --- | ---: |
| `EFICIENTE` | 1.543 |
| `MODERADO` | 1.976 |
| `INEFICIENTE` | 1.481 |

SHA-256 do CSV:

```text
6c147517fce6108f0f663d72c41428736325e248db171a8357050ab02c8a73a3
```

O dataset é integralmente sintético e permanece identificado como:

```text
2.0.0-candidate
```

---

## 5. Split e isolamento metodológico

O split oficial foi centralizado e estratificado:

| Conjunto | Proporção | Registros |
| --- | ---: | ---: |
| Treino | 70% | 3.500 |
| Validação | 15% | 750 |
| Holdout | 15% | 750 |

O isolamento metodológico adotado foi:

**Dataset completo:** somente auditoria estrutural, schema, quantidade, tipos, domínios, nulos, finitude, limites, duplicações, quotas e coerência.

**Treino:** EDA decisória, correlação, PCA, K-Means e demais diagnósticos exploratórios.

**Treino e validação:** comparação de modelos, seleção, tuning, ablação, `permutation importance`, avaliação probabilística, calibração e análise necessária para congelamento.

**Holdout antes do congelamento:** somente verificações estruturais permitidas, sem consulta a distribuições, estatísticas ou métricas.

Nenhuma métrica do holdout foi utilizada para selecionar modelo, ajustar hiperparâmetros, escolher calibração, modificar features ou desempatar candidatos.

---

## 6. Diagnósticos de dependência multivariada

Os diagnósticos detalhados estão registrados em:

```text
data-science/docs/baseline-diagnostics-report-v2.md
```

Resultados principais da baseline logística:

| Configuração | F1-macro |
| --- | ---: |
| Modelo completo | 0,937864 |
| Melhor feature individual — `consumo_kwh` | 0,602815 |

A melhor feature individual atingiu aproximadamente 64,3% do resultado do modelo completo, abaixo do guardrail de 95%.

A ablação também registrou perda de desempenho ao remover features individualmente.

O `permutation importance` apresentou contribuição mensurável das features utilizadas.

Esses diagnósticos sustentam que, nas condições da base sintética testada, o comportamento observado não depende exclusivamente de uma única feature.

> **O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.**

---

## 7. Comparação dos modelos

Foram avaliados os cinco modelos previstos:

| Modelo | CV F1-macro médio | F1-macro validação |
| --- | ---: | ---: |
| Dummy | 0.188818 | 0.189112 |
| Regressão Logística | 0.930248 | 0.937864 |
| Árvore de Decisão | 0.861918 | 0.836870 |
| Random Forest | 0.951890 | 0.952086 |
| HistGradientBoosting | 0.953864 | 0.952011 |

Os dois finalistas foram:

```text
HistGradientBoosting
Random Forest
```

Somente esses dois modelos receberam ajuste controlado de hiperparâmetros.

Como a diferença ficou abaixo de `0,01` F1-macro, a decisão considerou também estabilidade, simplicidade, velocidade e comportamento probabilístico.

O holdout não foi utilizado para desempate.

---

## 8. Tuning dos finalistas

### HistGradientBoosting

Configuração selecionada:

```text
l2_regularization = 0
learning_rate = 0.10
max_iter = 100
max_leaf_nodes = 15
```

Resultados:

```text
CV F1-macro:        0.9551602686
Validação F1-macro: 0.9468575099
```

### Random Forest

Configuração selecionada:

```text
n_estimators = 200
max_features = "sqrt"
min_samples_leaf = 1
random_state = 42
n_jobs = 1
```

Resultados:

```text
CV F1-macro:        0.9536312658
Validação F1-macro: 0.9521084976
```

---

## 9. Avaliação probabilística e calibração

Foram comparadas as probabilidades:

```text
raw
sigmoid
isotonic
```

### HistGradientBoosting

| Método | F1-macro | Log loss | Brier multiclasses |
| --- | ---: | ---: | ---: |
| raw | 0.946857510 | 0.121626020 | 0.074170027 |
| sigmoid | 0.950824208 | 0.137261629 | 0.076597685 |
| isotonic | 0.953393041 | 0.131847721 | 0.076767417 |

### Random Forest

| Método | F1-macro | Log loss | Brier multiclasses |
| --- | ---: | ---: | ---: |
| raw | 0.952108498 | 0.117367785 | 0.067300867 |
| sigmoid | 0.952095730 | 0.119789553 | 0.070086911 |
| isotonic | 0.950792155 | 0.097514145 | 0.063736274 |

A calibração isotônica do Random Forest foi adotada porque apresentou melhoria de qualidade probabilística em log loss e Brier multiclasses, com pequena redução de F1-macro dentro do guardrail metodológico definido.

---

## 10. Solução congelada

A solução final congelada antes do holdout foi:

```text
Random Forest + isotonic
```

Configuração:

```text
RandomForestClassifier
n_estimators = 200
max_features = "sqrt"
min_samples_leaf = 1
random_state = 42
n_jobs = 1

CalibratedClassifierCV
method = "isotonic"
ensemble = False
n_jobs = 1

StratifiedKFold
n_splits = 5
shuffle = True
random_state = 42
```

O pré-processamento congelado utiliza:

```text
Pipeline
ColumnTransformer
StandardScaler
OneHotEncoder(handle_unknown="ignore")
```

O Marco 2 foi validado explicitamente antes da primeira consulta às métricas oficiais do holdout.

---

## 11. Avaliação oficial única do holdout

Evidência estruturada:

```text
data-science/docs/holdout-evaluation-v2.json
data-science/docs/holdout-evaluation-v2.execution.json
```

Status:

```text
OFFICIAL_HOLDOUT_EVALUATED
official_evaluation_count = 1
holdout_size = 750
```

Métricas finais:

| Métrica | Resultado |
| --- | ---: |
| F1-macro | 0.9611680646163405 |
| Log loss | 0.12529593017111162 |
| Brier multiclasses | 0.052660778071399746 |

> **O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.**

Essas métricas passaram a ser exclusivamente métricas de avaliação final.

Nenhuma decisão posterior de modelo, hiperparâmetros, calibração ou features foi tomada com base nesses resultados.

---

## 12. Artefato final

Modelo:

```text
data-science/models/modelo_energetico_v2.joblib
```

SHA-256:

```text
ba4a2d8df87d0e0d6f4226a7b782f193a16c9722029c45cf2ab17a707532380e
```

Metadados:

```text
data-science/models/modelo_energetico_v2.metadata.json
```

Classes expostas pelo artefato:

```text
EFICIENTE
INEFICIENTE
MODERADO
```

O artefato serializado corresponde à solução utilizada na avaliação oficial do holdout.

Nenhum refit foi realizado após essa avaliação.

---

## 13. Testes de contrato

Os testes específicos do artefato estão em:

```text
data-science/tests/test_model_contract.py
```

Eles verificam, sem executar novo fitting e sem acessar o holdout:

- identidade e SHA-256 do modelo;
- configuração congelada;
- classes;
- calibração isotônica;
- validação cruzada;
- pipeline e pré-processamento;
- parâmetros do Random Forest;
- contrato das cinco features;
- inferência determinística com entradas sintéticas;
- compatibilidade com o loader da FastAPI.


### Cobertura automatizada adicional

Além dos testes específicos do artefato, a entrega contém cobertura automatizada para qualidade do dataset, artefato do dataset, recomendações, serviço de inferência, endpoints da FastAPI e validação do notebook:

```text
data-science/tests/test_dataset.py
data-science/tests/test_dataset_artifact.py
data-science/tests/test_recommendation_service.py
data-science/tests/test_model_contract.py
data-science/tests/test_inference_service.py
data-science/tests/test_health.py
data-science/tests/test_predict.py
data-science/tests/test_validate_notebook.py
```

A execução completa da suíte e a sincronização com `develop` foram concluídas
na integração da Issue #86. A validação ponta a ponta posterior pertence à
Issue #121 e está documentada em [frontend/e2e/README.md](../../frontend/e2e/README.md).

### Preservação do notebook histórico

O notebook histórico permanece versionado em:

```text
data-science/notebooks/prototipo_base_sintetica.ipynb
```

Neste checkpoint, o arquivo não apresenta alterações no diff local e permanece preservado como artefato histórico.

---

## 14. Exemplos reproduzíveis de inferência

Arquivo:

```text
data-science/examples/exemplos_predict_v2.json
```

O conjunto contém seis requests sintéticos cobrindo:

```text
CASA
APARTAMENTO
COMERCIO
ESCRITORIO
INDUSTRIA
OUTRO
```

SHA-256:

```text
e842a7dc16b2660395b349ea7355bf821fb3c409ad9a2b84922a6d5bf5982c75
```

Os exemplos utilizam exclusivamente as cinco features públicas e foram reproduzidos pelo artefato oficial por meio do `InferenceService`.

Esses exemplos demonstram o contrato técnico de inferência e não representam avaliação adicional de desempenho.

---

## 15. Validação local da FastAPI

A FastAPI existente foi validada localmente com:

```text
MODEL_PATH=./models/modelo_energetico_v2.joblib
MODEL_VERSION=energy-classifier-v2
```

### `/health`

Resultado:

```http
GET /health
HTTP 200
```

Resposta:

```json
{"status":"UP"}
```

O startup da aplicação concluiu com carregamento do artefato final.

### `/predict`

Foi utilizado um payload sintético:

```json
{
  "consumo_kwh": 180.0,
  "uso_horario_pico": false,
  "quantidade_equipamentos": 4,
  "tipo_imovel": "CASA",
  "horas_alto_consumo": 1
}
```

Resultado:

```http
POST /predict
HTTP 200
Content-Type: application/json
```

Resposta:

```json
{
  "categoria": "EFICIENTE",
  "probabilidade": 1.0,
  "score": 0,
  "recomendacoes": [
    "Acompanhar o consumo mensal e manter práticas de uso consciente de energia."
  ],
  "modelo_versao": "energy-classifier-v2"
}
```

A resposta foi comparada por `httpx` com o exemplo versionado e apresentou:

```text
CATEGORY_MATCH=True
PROBABILITY_MATCH=True
SCORE_MATCH=True
RECOMMENDATIONS_MATCH=True
MODEL_VERSION_MATCH=True
EXACT_RESPONSE_MATCH=True
```

Uma divergência textual observada inicialmente com `Invoke-WebRequest` foi isolada como problema de decodificação do cliente PowerShell. A validação com `httpx` confirmou a resposta UTF-8 esperada.

Nenhum dado do holdout foi utilizado nessa validação.

---

## 16. Ambiente registrado

Ambiente da avaliação oficial:

```text
Python: 3.12.10
scikit-learn: 1.9.0
pandas: 3.0.5
joblib: 1.5.3
```

O runtime local da API foi executado com as dependências instaladas no ambiente do projeto.

---

## 17. Rastreabilidade

| Item | Referência |
| --- | --- |
| Issue | `#86` |
| PR histórica | `#122` — concluída e integrada |
| Branch histórica | `feature/86/dataset-modelagem-v2` |
| Commit de integração | `85f6acd` — Issue `#86` concluída |
| Dataset SHA-256 | `6c147517fce6108f0f663d72c41428736325e248db171a8357050ab02c8a73a3` |
| Modelo SHA-256 | `ba4a2d8df87d0e0d6f4226a7b782f193a16c9722029c45cf2ab17a707532380e` |
| Exemplos SHA-256 | `e842a7dc16b2660395b349ea7355bf821fb3c409ad9a2b84922a6d5bf5982c75` |
| Modelo | `Random Forest + isotonic` |
| Dataset | `2.0.0-candidate` |
| Modelo runtime | `energy-classifier-v2` |
| Holdout oficial | Avaliado exatamente uma vez |

Documentos complementares:

```text
data-science/docs/dataset-model-specification-v2.md
data-science/docs/target-generation-review-v2.md
data-science/docs/baseline-diagnostics-report-v2.md
data-science/docs/holdout-evaluation-v2.json
data-science/docs/holdout-evaluation-v2.execution.json
data-science/models/modelo_energetico_v2.metadata.json
data-science/examples/exemplos_predict_v2.json
```

O documento `target-generation-review-v2.md` permanece como registro histórico da fase de revisão metodológica e não deve ser interpretado como o estado final da solução.

---

## 18. Limitações

O dataset é integralmente sintético.

As relações existentes foram construídas sob premissas controladas do projeto.

As métricas não demonstram desempenho em dados reais.

Não há evidência de causalidade ou validade externa.

A calibração foi avaliada exclusivamente no contexto sintético disponível.

Os exemplos de `/predict` verificam contrato e reprodutibilidade, não desempenho.

A validação local da FastAPI não substitui integração completa com Spring Boot, deploy OCI ou teste E2E.

> **O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.**

---

## 19. Fechamento do Marco 3

Concluído nesta entrega:

```text
avaliação oficial única do holdout
serialização do modelo
metadados e hashes
testes de contrato
exemplos reproduzíveis
validação local de /health
validação local de /predict
relatório final de modelagem
```

Os checkpoints de sincronização, suíte completa, CI e revisão final foram
concluídos no fechamento da Issue #86. Este relatório mantém a delimitação de
escopo original: a publicação FastAPI e a validação E2E completa são entregas
posteriores, registradas nas fontes específicas de deploy e E2E.
