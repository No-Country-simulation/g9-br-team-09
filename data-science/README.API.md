# API de Inferência EnergIAI

## Objetivo e arquitetura

FastAPI executa inferência interna consumida exclusivamente por Spring Boot:

~~~text
Frontend -> Spring Boot -> FastAPI -> modelo energético
~~~

Frontend não deve chamá-la diretamente. Schemas, constraints, score, probabilidade e compatibilidade Java/Python estão no [contrato normativo](../docs/api-contract.md).

## Pré-requisitos e instalação

Use Python 3.14.

~~~bash
cd data-science
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements-api.txt
~~~

## Configuração e execução local

~~~bash
cp .env.api.example .env.api
~~~

~~~dotenv
MODEL_PATH=./models/<ARTEFATO_COMPATIVEL>
MODEL_VERSION=<VERSAO_DO_MODELO>
~~~

Modelo é carregado uma vez no startup. Sem MODEL_PATH, MODEL_VERSION ou artefato compatível com predict_proba e três classes oficiais, aplicação falha ao iniciar; não cria modelo fake nem aplica fallback.

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

## Dependência e limitações

Artefato V2 oficial depende da Issue #86. Testes de contrato usam fakes injetados, mas teste contra modelo real permanece bloqueado até artefato compatível. Fora de escopo: treinamento, notebooks históricos, acesso frontend, fallback backend, integração ponta a ponta, credenciais Oracle, OCI e deploy.
