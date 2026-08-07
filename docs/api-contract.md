# Contrato de integração de APIs — EnergiAI

Este documento é a fonte normativa dos contratos HTTP: API pública Spring Boot e API interna FastAPI. Guias operacionais devem referenciá-lo, não redefini-lo.

## Arquitetura e responsabilidades

~~~text
Frontend
   |
   | API pública
   v
Spring Boot
POST /api/v1/analise-energetica
   |
   | API interna
   v
FastAPI
POST /predict
   |
   v
Modelo de Machine Learning
~~~

O frontend consome somente Spring Boot e nunca /predict. FastAPI não é API pública. Spring Boot responde por autenticação, validação pública, orquestração, custo, persistência, fallback e resposta pública. FastAPI responde por inferência, categoria, probabilidade, score, recomendações ML e versão do modelo.

## API pública — Spring Boot

### POST /api/v1/analise-energetica

Exige Authorization: Bearer <access_token>. O request usa snake_case:

~~~json
{"consumo_kwh":420,"uso_horario_pico":true,"quantidade_equipamentos":10,"tipo_imovel":"CASA","horas_alto_consumo":8}
~~~

| Campo | Tipo | Regra |
| --- | --- | --- |
| consumo_kwh | number | maior que zero |
| uso_horario_pico | boolean | obrigatório |
| quantidade_equipamentos | integer | maior ou igual a 1 |
| tipo_imovel | string | CASA, APARTAMENTO, COMERCIO, ESCRITORIO, INDUSTRIA ou OUTRO |
| horas_alto_consumo | integer | entre 0 e 24 |

A resposta pública contém id, categoria, probabilidade, score, custo_estimado_mensal, recomendacoes e fonte_classificacao:

~~~json
{"id":1,"categoria":"INEFICIENTE","probabilidade":0.75,"score":95,"custo_estimado_mensal":315.00,"recomendacoes":["Reduzir o uso de equipamentos durante horários de pico."],"fonte_classificacao":"RULE_BASED_FALLBACK"}
~~~

| Campo | Descrição |
| --- | --- |
| id | Identificador da análise persistida. |
| categoria | Categoria final: EFICIENTE, MODERADO ou INEFICIENTE. |
| probabilidade | Probabilidade do modelo ou confiança heurística do classificador por regras. |
| score | Score numérico da análise. |
| custo_estimado_mensal | Estimativa mensal em reais calculada pelo backend. |
| recomendacoes | Recomendações da análise. |
| fonte_classificacao | RULE_BASED, ML_MODEL ou RULE_BASED_FALLBACK. |

Categorias: EFICIENTE, MODERADO e INEFICIENTE. O backend calcula custo_estimado_mensal como consumo_kwh * 0.75. RULE_BASED, ML_MODEL e RULE_BASED_FALLBACK identificam a estratégia. Em regras, 0.75 é confiança heurística, não acurácia nem probabilidade estatística.

modelo_versao é exclusivo do contrato FastAPI → Spring Boot. Não integra EnergyAnalysisResponse e não é exposto ao frontend.

As operações de análise usam o usuário autenticado: a criação associa o registro a esse usuário, a listagem retorna somente seus registros, o detalhe por id exige que o registro lhe pertença e o resumo/dashboard é calculado somente sobre seus dados.

Erros públicos Spring Boot têm timestamp, status, error e message. Códigos existentes: VALIDATION_ERROR, ENUM_TYPE_ERROR, INVALID_TYPE_ERROR, HTTP_MESSAGE_ERROR, BAD_REQUEST_ERROR, NOT_FOUND_ERROR, METHOD_NOT_ALLOWED_ERROR, UNSUPPORTED_MEDIA_TYPE_ERROR, INTERNAL_ERROR, CONFLICT_ERROR, UNAUTHORIZED_ERROR e FORBIDDEN_ERROR. Detalhes internos não são expostos.

### Autenticação, refresh e logout

O access token é JWT assinado com HS256, de curta duração, retornado no JSON de login e refresh e usado como Authorization: Bearer <access_token>. Ele não é persistido nem recebe blacklist no logout atual; um token já emitido pode continuar válido até expirar após logout da sessão de refresh.

POST /api/v1/auth/login retorna access_token, token_type, expires_in e usuario, além de emitir refresh_token somente em cookie HttpOnly, cookie XSRF-TOKEN não HttpOnly e header X-XSRF-TOKEN. O refresh token é opaco, tem ao menos 256 bits de entropia, não aparece no JSON, não deve ser lido por JavaScript e somente seu hash SHA-256 é persistido. GET /api/v1/auth/me retorna os dados do usuário autenticado.

Cada login cria uma família de sessão. Refresh válido rotaciona obrigatoriamente o token e mantém o sucessor na mesma família. A família tem expiração absoluta; a validade efetiva do sucessor é o menor valor entre a duração configurada e o fim da família.

POST /api/v1/auth/refresh não recebe body; exige os cookies refresh_token e XSRF-TOKEN e o header X-XSRF-TOKEN correspondente. Retorna 200 com novo access token e cookie de refresh rotacionado; falha de autenticação retorna HTTP 401 com UNAUTHORIZED_ERROR e CSRF ausente ou inválido retorna HTTP 403 com FORBIDDEN_ERROR. Reutilização concorrente do predecessor dentro de AUTH_REFRESH_REUSE_GRACE_PERIOD retorna 401 sem revogar a família nem remover cookie que possa conter sucessor de outra requisição. Após essa janela, a reutilização é tratada como reuso indevido e revoga a família.

POST /api/v1/auth/logout usa a mesma proteção CSRF, é idempotente, retorna 204 e remove os cookies. O logout revoga a sessão de refresh apresentada, sem invalidar antecipadamente access token já emitido. CSRF é aplicado a POST /api/v1/auth/refresh e POST /api/v1/auth/logout, não aos endpoints protegidos por Bearer em geral.

O cliente web deve usar credentials: "include". CORS usa origens explícitas, credenciais e permite/exibe X-XSRF-TOKEN; wildcard não é aceito com credenciais. AUTH_REFRESH_* configura duração do token, duração da família, janela de tolerância e atributos do cookie.

No profile local, HTTP usa cookie com Secure=false. Fora do cenário cross-site, o padrão configurado é SameSite=Strict. Na OCI, quando frontend e backend estão em sites diferentes, SameSite=None exige Secure=true para envio cross-site. SameSite=None; Secure não garante que cookies de terceiros serão aceitos: Safari/WebKit e políticas restritivas podem bloqueá-los; valide em navegador real e considere topologia same-site para compatibilidade mais ampla.

## API interna — FastAPI

### POST /predict

Consumidor: Spring Boot somente. Não há prefixo /api/v1.

#### Request

~~~json
{"consumo_kwh":420.0,"uso_horario_pico":true,"quantidade_equipamentos":10,"tipo_imovel":"CASA","horas_alto_consumo":8}
~~~

Campos extras são rejeitados.

| Campo | Contrato FastAPI |
| --- | --- |
| consumo_kwh | float estrito, finito e maior que zero |
| uso_horario_pico | boolean estrito |
| quantidade_equipamentos | inteiro estrito e maior ou igual a 1 |
| tipo_imovel | CASA, APARTAMENTO, COMERCIO, ESCRITORIO, INDUSTRIA ou OUTRO |
| horas_alto_consumo | inteiro estrito entre 0 e 24 |

#### Response

~~~json
{"categoria":"INEFICIENTE","probabilidade":0.81,"score":81,"recomendacoes":["Reduzir o uso de equipamentos durante horários de pico."],"modelo_versao":"energy-classifier-v2"}
~~~

Números e versão são exemplos, não resultado garantido nem artefato final da Issue #86.

- categoria: EFICIENTE, MODERADO ou INEFICIENTE, definida por argmax das probabilidades das três classes; não é recalculada pelo score.
- probabilidade: número finito de 0 a 1 associado à categoria escolhida por argmax. Não é necessariamente calibrada; pipeline final com calibração tecnicamente justificada mantém o mesmo campo.
- score: severidade esperada de 0 a 100:

~~~text
round(0 * P(EFICIENTE) + 50 * P(MODERADO) + 100 * P(INEFICIENTE))
~~~

Score não é confiança e não redefine categoria.
- recomendacoes: lista obrigatória, não vazia, textos não vazios e sem duplicações. Em ML_MODEL, backend usa recomendações válidas FastAPI.
- modelo_versao: string obrigatória não vazia que identifica modelo/artefato, não versão HTTP; não é pública no backend.

### Aceitação pelo Spring Boot

O orquestrador aceita response não nulo, categoria não nula, probabilidade não nula/finita/de 0 a 1, score de 0 a 100 e recomendacoes não nulas, não vazias e sem itens nulos. Não valida modelo_versao atualmente.

FastAPI é mais estrita: recomendações devem ter textos não vazios, sem duplicações, e modelo_versao não vazio. A diferença é documentada, não corrigida nesta issue.

### Erros FastAPI

Payload incompatível recebe resposta de validação Pydantic/FastAPI, HTTP 422; ela não é ApiErrorResponse Spring Boot. Erro de inferência recebe HTTP 500 sanitizado:

~~~json
{"detail":"Não foi possível executar a inferência."}
~~~

Ausência de configuração ou artefato compatível pode impedir startup; não é response de /predict.

## Timeout e fallback

Backend usa ML_API_BASE_URL, ML_API_CONNECT_TIMEOUT e ML_API_READ_TIMEOUT, com defaults http://localhost:8000, 2s e 5s. Indisponibilidade, timeout, erro HTTP, transporte, response ausente ou incompatível acionam classificador local quando possível, com fonte_classificacao = RULE_BASED_FALLBACK.

RULE_BASED_FALLBACK pertence ao Spring Boot. FastAPI não o envia e não implementa fallback. fonte_classificacao = ML_MODEL só ocorre após Spring Boot aceitar response FastAPI válida.

## Evolução compatível

Nomes, tipos e semântica não mudam unilateralmente. Breaking changes exigem coordenação Java/Python. Algoritmo ou artefato pode mudar sem alterar HTTP se invariantes permanecerem; calibração não altera formato. modelo_versao identifica modelo, não contrato. Nenhuma evolução permite frontend consumir /predict. Mudanças atualizam esta fonte normativa antes ou junto do código.

## Limites da Issue #86

Contrato estável: cinco features, categorias, argmax, probabilidade da categoria, score de severidade 0–100, recomendações, modelo_versao e formato de /predict.

Pendente na #86: modelo vencedor, hiperparâmetros, métricas, holdout, decisão de calibração, artefato serializado final e versão concreta. Isso não bloqueia contrato HTTP.
