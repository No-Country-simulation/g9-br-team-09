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
  "probabilidade": 0.95,
  "score": 95,
  "custo_estimado_mensal": 315.00,
  "recomendacoes": [
    "Reduzir o uso de equipamentos durante horários de pico.",
    "Avaliar equipamentos com alto consumo energético.",
    "Distribuir o consumo ao longo do dia.",
    "Verificar a eficiência energética dos equipamentos."
  ],
  "fonte_classificacao": "RULE_BASED"
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

No estado atual do backend, a classificação documentada nesta branch é retornada com `fonte_classificacao = RULE_BASED`.

## Cálculo de custo estimado

Tarifa de referência atual:

```text
R$ 0,75/kWh
```

Fórmula:

```text
custo_estimado_mensal = consumo_kwh * 0.75
```

## Exemplo adicional de requisição moderada

```json
{
  "consumo_kwh": 260,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 8,
  "tipo_imovel": "APARTAMENTO",
  "horas_alto_consumo": 5
}
```

## Exemplo adicional de resposta moderada

```json
{
  "categoria": "MODERADO",
  "probabilidade": 0.60,
  "score": 60,
  "custo_estimado_mensal": 195.00,
  "recomendacoes": [
    "Reduzir o uso de equipamentos durante horários de pico.",
    "Distribuir o consumo ao longo do dia."
  ],
  "fonte_classificacao": "RULE_BASED"
}
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

## Estado atual e arquitetura-alvo

Estado atual:

- O backend recebe e valida o request.
- O backend executa atualmente a classificação local baseada em regras.
- O backend calcula o custo estimado.
- O backend gera as recomendações disponíveis atualmente.
- O backend monta e retorna o contrato público.
- A fonte atual da classificação é `RULE_BASED`.

Arquitetura-alvo:

- Data Science disponibilizará o modelo ou classificação por meio da integração definida pelo time.
- Data Science poderá fornecer recomendações caso essa responsabilidade seja confirmada no contrato de integração.
- O backend continuará responsável pela API pública, validação, orquestração, cálculo de custo, persistência e resposta final.
- Em falha, timeout ou resposta inválida da integração com Data Science, o backend poderá utilizar `RULE_BASED_FALLBACK`.
