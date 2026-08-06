# Contrato da API — EnergIAI

Este documento é a referência principal do contrato público atual da API do EnergIAI.

## Endpoint público

```http
POST /api/v1/analise-energetica
```

O prefixo `/api/v1` faz parte do contrato público implementado no backend.

## Objetivo do endpoint

Receber dados de consumo energético, executar a análise e retornar classificação, custo estimado, recomendações e a fonte da classificação.

## Request oficial

```json
{
  "consumo_kwh": 420,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 10,
  "tipo_imovel": "CASA",
  "horas_alto_consumo": 8
}
```

## Regras da requisição

| Campo                     | Tipo      | Obrigatório | Regra                                       |
| ------------------------- | --------- | ----------- | ------------------------------------------- |
| `consumo_kwh`             | `number`  | Sim         | Deve ser maior que zero                     |
| `uso_horario_pico`        | `boolean` | Sim         | Não pode ser nulo                           |
| `quantidade_equipamentos` | `integer` | Sim         | Deve ser maior ou igual a 1                 |
| `tipo_imovel`             | `string`  | Sim         | Deve corresponder a um valor válido do enum |
| `horas_alto_consumo`      | `integer` | Sim         | Deve estar entre 0 e 24                     |

## Enum `tipo_imovel`

Valores aceitos pelo contrato atual:

- `CASA`
- `APARTAMENTO`
- `COMERCIO`
- `ESCRITORIO`
- `INDUSTRIA`
- `OUTRO`

## Response oficial

```json
{
  "categoria": "INEFICIENTE",
  "probabilidade": 0.75,
  "score": 95,
  "custo_estimado_mensal": 315.00,
  "recomendacoes": [
    "Reduzir o uso de equipamentos durante horários de pico.",
    "Avaliar equipamentos com alto consumo energético.",
    "Distribuir o consumo ao longo do dia.",
    "Verificar a eficiência energética dos equipamentos."
  ],
  "fonte_classificacao": "RULE_BASED_FALLBACK"
}
```

## Campos da resposta

| Campo                     | Tipo      | Descrição |
| ------------------------- | --------- | --------- |
| `categoria`               | `string`  | Categoria energética retornada pela análise. |
| `probabilidade`           | `number`  | Probabilidade estimada da classificação. |
| `score`                   | `integer` | Score calculado pela classificação. |
| `custo_estimado_mensal`   | `number`  | Estimativa mensal em reais com base na tarifa de referência. |
| `recomendacoes`           | `array`   | Recomendações geradas para o perfil analisado. |
| `fonte_classificacao`     | `string`  | Origem da classificação retornada. |

## Enum `categoria`

Valores públicos atuais:

- `EFICIENTE`
- `MODERADO`
- `INEFICIENTE`

## `fonte_classificacao`

Valores possíveis no contrato:

- `RULE_BASED`: classificação realizada diretamente pelo classificador local do backend.
- `ML_MODEL`: classificação retornada pelo modelo ou API de Data Science.
- `RULE_BASED_FALLBACK`: a aplicação tentou usar a integração com Data Science, mas utilizou o classificador local por erro, timeout ou resposta inválida.

O valor `0.75` é uma confiança heurística convencional do classificador baseado
em regras. Ele não representa uma taxa de acurácia medida, uma probabilidade
estatística nem um valor obtido por calibração.

## Semântica de score e probabilidade

O `score` é um índice de ineficiência energética de `0` a `100`; ele determina
a categoria, mas não representa a confiança da classificação.

Quando `fonte_classificacao` for `ML_MODEL`, `probabilidade` contém o valor
produzido pelo modelo de Machine Learning. Quando for `RULE_BASED` ou
`RULE_BASED_FALLBACK`, `probabilidade` contém a confiança heurística fixa
`0.75`. Essa confiança não é uma probabilidade estatística produzida por um
modelo e não deve ser calculada a partir do `score`.

No fluxo do endpoint, `fonte_classificacao` é `ML_MODEL` quando a integração
com Machine Learning retorna uma resposta válida e `RULE_BASED_FALLBACK` quando
ocorre falha, timeout ou resposta inválida da API de ML.

## Cálculo de custo estimado

Tarifa de referência atual:

```text
R$ 0,75/kWh
```

Fórmula:

```text
custo_estimado_mensal = consumo_kwh * 0.75
```

## Tratamento de erros

Formato atual documentado para erros de validação:

```json
{
  "timestamp": "2026-07-10T18:30:00",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "consumo_kwh: O consumo deve ser um valor positivo"
}
```

- `status` representa o status HTTP.
- `error` contém um código estável e legível por máquina.
- `message` contém a explicação legível para o consumidor.
- Erros internos não devem expor stack trace ou detalhes sensíveis.

Outros códigos de erro já previstos na implementação atual incluem `ENUM_TYPE_ERROR`, `INVALID_TYPE_ERROR`, `HTTP_MESSAGE_ERROR`, `NOT_FOUND_ERROR`, `METHOD_NOT_ALLOWED_ERROR`, `UNSUPPORTED_MEDIA_TYPE_ERROR` e `INTERNAL_ERROR`.

## Estado atual

- O backend recebe e valida o request.
- O backend tenta obter a classificação pela integração com Machine Learning.
- Respostas válidas da API de ML usam `fonte_classificacao = ML_MODEL`.
- Em caso de falha, timeout ou resposta inválida, o backend utiliza o
  classificador local com `fonte_classificacao = RULE_BASED_FALLBACK`.
- O backend calcula o custo estimado, gera recomendações, persiste a análise e
  retorna o contrato público.

## Autenticação e renovação de sessão

O access token continua sendo um JWT assinado com `HS256`, enviado no JSON de
login e refresh e utilizado como `Bearer`. Ele é de curta duração, não é
persistido e não recebe blacklist no logout desta versão.

O refresh token é um valor opaco com pelo menos 256 bits de entropia. Ele nunca
faz parte do JSON: é enviado somente no cookie `refresh_token`, com `HttpOnly`,
e apenas seu hash `SHA-256` é persistido. Cada login cria uma nova família de
sessão e cada refresh válido rotaciona obrigatoriamente o token dentro da mesma
família.

### `POST /api/v1/auth/login`

Mantém o `AuthenticationResponse` existente no corpo e também emite:

- o cookie HttpOnly `refresh_token`;
- o cookie não HttpOnly `XSRF-TOKEN`;
- o header `X-XSRF-TOKEN`, com o valor a ser enviado pelo cliente nas operações
  protegidas por CSRF.

### `POST /api/v1/auth/refresh`

Não recebe body. Exige o cookie `refresh_token`, o cookie `XSRF-TOKEN` e o
header `X-XSRF-TOKEN` correspondente. Uma rotação bem-sucedida retorna `200`
com um novo access token no mesmo contrato JSON do login e substitui o cookie
de refresh.

Falhas de autenticação do refresh retornam `401` com `UNAUTHORIZED_ERROR` e
mensagem genérica. CSRF ausente ou inválido retorna `403` com
`FORBIDDEN_ERROR`. A reutilização concorrente do predecessor dentro da janela
de tolerância retorna `401` sem apagar o cookie que pode conter o sucessor já
emitido. Reutilização posterior à tolerância revoga a família.

### `POST /api/v1/auth/logout`

Não recebe body e exige a mesma proteção CSRF do refresh. O endpoint é
idempotente, retorna `204` e remove os cookies de refresh e CSRF. O logout
revoga a sessão de refresh apresentada, mas não invalida antecipadamente um
access token já emitido.

### Cookies, CORS e expiração

O cliente web deve usar `credentials: "include"`. CORS mantém
`allowCredentials=true`, origens explícitas, o request header
`X-XSRF-TOKEN` permitido e o response header homônimo exposto; wildcard não é
aceito com credenciais.

As validades do token e da família, a janela de tolerância e os atributos do
cookie são configuráveis pelas variáveis `AUTH_REFRESH_*`. A validade do
sucessor é o menor valor entre sua duração configurada e o fim absoluto da
família. Localmente, o cookie usa `Secure=false` para HTTP. Na OCI, frontend e
API em sites diferentes exigem `SameSite=None; Secure`; por padrão, fora desse
cenário, usa-se `SameSite=Strict`.

`SameSite=None; Secure` é necessário para o fluxo cross-site, mas não contorna
bloqueios de cookies de terceiros impostos pelo navegador. Safari/WebKit e
ambientes com políticas restritivas podem bloquear os cookies; valide a
integração manualmente em navegador real. Compatibilidade ampla pode exigir no
futuro uma topologia same-site.
