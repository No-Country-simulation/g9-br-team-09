# API de Inferência EnergIAI

## Objetivo e arquitetura

Esta API FastAPI executa a inferência do modelo energético oficial e é um
contrato interno consumido exclusivamente pelo backend Spring Boot:

```text
Frontend -> Backend Spring Boot -> FastAPI -> modelo energético
```

O frontend não deve chamar esta API diretamente. O backend continua responsável
pela API pública, custo, persistência, orquestração e fallback local. Esta API
retorna apenas classificação, probabilidade, score, recomendações e versão do
modelo.

## Pré-requisitos e instalação

Use Python 3.14, a versão adotada pelo ambiente de Data Science do projeto.

```bash
cd data-science
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements-api.txt
```

## Configuração e execução local

Copie o exemplo e informe o artefato oficial disponibilizado pela issue #86:

```bash
cp .env.api.example .env.api
```

```dotenv
MODEL_PATH=./models/modelo_energetico_v2.joblib
MODEL_VERSION=energy-classifier-v2
```

O modelo é carregado uma única vez durante a inicialização. Sem `MODEL_PATH`,
`MODEL_VERSION`, ou um artefato compatível com `predict_proba` e as três classes
oficiais, a aplicação falha ao iniciar; ela não cria modelo fake nem aplica
fallback de classificação.

```bash
cd data-science
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Os recursos OpenAPI nativos estão disponíveis em `/docs`, `/redoc` e
`/openapi.json`.

## Endpoints

### `GET /health`

Retorna `200` e exatamente `{"status":"UP"}` depois que o serviço de
inferência está configurado. Não expõe o caminho do modelo ou detalhes internos.

```bash
curl --fail http://localhost:8000/health
```

### `POST /predict`

```bash
curl --fail \
  --request POST \
  --header "Content-Type: application/json" \
  --data '{
    "consumo_kwh": 420.0,
    "uso_horario_pico": true,
    "quantidade_equipamentos": 10,
    "tipo_imovel": "CASA",
    "horas_alto_consumo": 8
  }' \
  http://localhost:8000/predict
```

A resposta contém `categoria`, `probabilidade`, `score`, `recomendacoes` e
`modelo_versao`, todos em `snake_case` e compatíveis com `MlPredictionResponse`.

Categoria e probabilidade vêm do `argmax` de `predict_proba`, respeitando a
ordem declarada em `classes_`. O score é a severidade esperada:

```text
round(0 * P(EFICIENTE) + 50 * P(MODERADO) + 100 * P(INEFICIENTE))
```

O motor de recomendações usa limites operacionais próprios e constantes
nomeadas para horário de pico, horas de alto consumo, consumo e quantidade de
equipamentos. Esses limites não classificam a predição nem substituem a
categoria do modelo.

## Testes

```bash
cd data-science
python -m pytest tests -q
python -m compileall app tests
```

Os testes injetam um modelo fake determinístico somente no ambiente de teste;
nenhum artefato fake é versionado como modelo oficial.

## Dependência e itens fora de escopo

O artefato V2 oficial ainda depende da issue #86. Até ele estar disponível, o
teste contra o modelo real permanece bloqueado, embora a API e seus testes de
contrato possam ser executados com fakes injetados.

Estão fora de escopo: treinamento, notebooks históricos, acesso direto pelo
frontend, fallback `RULE_BASED_FALLBACK`, integração ponta a ponta com Spring
Boot, credenciais Oracle, OCI e qualquer deploy.
