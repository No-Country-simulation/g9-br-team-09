# API de Inferência EnergIAI

## Objetivo e arquitetura

FastAPI executa inferência interna consumida exclusivamente por Spring Boot:

~~~text
Frontend -> Spring Boot -> FastAPI -> modelo energético
~~~

Frontend não deve chamá-la diretamente. Schemas, constraints, score, probabilidade e compatibilidade Java/Python estão no [contrato normativo](../docs/api-contract.md).

## Pré-requisitos e instalação

Use Python 3.12.

### Runtime da API

~~~bash
cd data-science
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements-api.txt
~~~

### Ambiente completo de desenvolvimento

Para testes, geração de dataset, modelagem e notebooks:

~~~bash
cd data-science
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
~~~

## Configuração e execução local

~~~bash
cp .env.api.example .env.api
~~~

~~~dotenv
MODEL_PATH=./models/<ARTEFATO_COMPATIVEL>
MODEL_VERSION=<VERSAO_DO_MODELO>
~~~

O exemplo versionado usa `./models/modelo_energetico_v2.joblib` e
`energy-classifier-v2`. O modelo é carregado uma vez no startup. Sem
`MODEL_PATH`, `MODEL_VERSION` ou artefato compatível com `predict_proba` e as
três classes oficiais, a aplicação falha ao iniciar; não cria modelo fake nem
aplica fallback.

~~~bash
cd data-science
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
~~~

OpenAPI: /docs, /redoc e /openapi.json.

## Operação

GET /health retorna HTTP 200 e {"status":"UP"} somente após serviço configurado, sem expor caminho do modelo ou detalhes internos:

~~~bash
curl --fail http://localhost:8000/health
~~~

Para chamada manual de desenvolvimento:

~~~bash
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
~~~

O [contrato normativo](../docs/api-contract.md) define schema, constraints, response, categoria, probabilidade, score e evolução. Produção integra pelo Spring Boot, não pelo frontend.

## Testes

~~~bash
cd data-science
python -m pytest tests -q
python -m compileall app tests
~~~

Testes injetam modelo fake determinístico somente em teste; nenhum artefato fake é modelo oficial.

## Estado e limitações

A Issue #86 concluiu o artefato V2 e a Issue #111 publicou esta FastAPI no
Render. O serviço continua interno: o frontend não recebe nem deve consumir sua
URL; Spring Boot controla o contrato público e chama `/predict`.

O [relatório final de modelagem V2](docs/modeling-final-report-v2.md) é a fonte
detalhada de dataset, modelo, métricas, artefato e limitações. Testes de
contrato ainda usam fakes injetados quando precisam isolar a API; isso não
substitui a validação integrada registrada na [suíte E2E](../frontend/e2e/README.md).

O dataset é sintético. As métricas não comprovam desempenho em dados reais,
causalidade ou validade externa. Fora de escopo deste README: treinamento,
notebooks históricos, acesso direto pelo frontend, fallback do backend,
credenciais Oracle e operação OCI.
