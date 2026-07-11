# Contrato da API — EnergIAI

Este documento registra o contrato inicial da API do projeto EnergIAI.

A documentação deve ser revisada pela equipe via Pull Request antes de ser considerada definitiva.

## Visão geral

A API tem como objetivo receber dados de consumo energético, processar a análise e retornar uma classificação de eficiência, recomendações de melhoria e custo estimado mensal.

## Endpoint obrigatório

```http
POST /analise-energetica
```

## Objetivo do endpoint

Receber dados de uma residência ou pequeno estabelecimento e retornar a análise energética em formato JSON.

## Corpo da requisição

```json
{
  "consumo_kwh": 420,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 10,
  "tipo_imovel": "Casa",
  "horas_alto_consumo": 8
}
```

## Campos da requisição

| Campo                     | Tipo      | Obrigatório | Descrição                             |
| ------------------------- | --------- | ----------- | ------------------------------------- |
| `consumo_kwh`             | `number`  | Sim         | Consumo mensal em kWh.                |
| `uso_horario_pico`        | `boolean` | Sim         | Indica uso em horário de pico.        |
| `quantidade_equipamentos` | `integer` | Sim         | Quantidade de equipamentos elétricos. |
| `tipo_imovel`             | `string`  | Sim         | Tipo do imóvel analisado.             |
| `horas_alto_consumo`      | `number`  | Sim         | Horas médias de alto consumo.         |

## Corpo da resposta esperada

```json
{
  "categoria": "Ineficiente",
  "probabilidade": 0.81,
  "recomendacoes": [
    "Reduzir o uso de equipamentos durante horários de pico",
    "Avaliar aparelhos com alto consumo energético",
    "Distribuir atividades de maior consumo ao longo do dia"
  ],
  "custo_estimado_mensal": 315.0
}
```

## Campos da resposta

| Campo                   | Tipo     | Descrição                             |
| ----------------------- | -------- | ------------------------------------- |
| `categoria`             | `string` | Classificação energética retornada.   |
| `probabilidade`         | `number` | Confiança estimada da classificação.  |
| `recomendacoes`         | `array`  | Recomendações de melhoria energética. |
| `custo_estimado_mensal` | `number` | Estimativa mensal em reais.           |

## Cálculo de custo estimado

A tarifa de referência do MVP é:

```text
R$ 0,75/kWh
```

Fórmula usada:

```text
custo_estimado_mensal = consumo_kwh * 0.75
```

Exemplo:

```text
420 * 0.75 = 315.00
```

## Categorias previstas

| Categoria     | Descrição                                            |
| ------------- | ---------------------------------------------------- |
| `Eficiente`   | Consumo controlado e menor risco energético.         |
| `Moderado`    | Perfil intermediário, com oportunidades de melhoria. |
| `Ineficiente` | Maior consumo ou maior risco de uso inadequado.      |

## Exemplo eficiente

### Requisição eficiente

```json
{
  "consumo_kwh": 120,
  "uso_horario_pico": false,
  "quantidade_equipamentos": 5,
  "tipo_imovel": "Apartamento",
  "horas_alto_consumo": 2
}
```

### Resposta eficiente

```json
{
  "categoria": "Eficiente",
  "probabilidade": 0.86,
  "recomendacoes": [
    "Manter hábitos atuais de consumo",
    "Acompanhar o consumo mensal para identificar variações",
    "Priorizar equipamentos com selo de eficiência energética"
  ],
  "custo_estimado_mensal": 90.0
}
```

## Exemplo moderado

### Requisição moderada

```json
{
  "consumo_kwh": 260,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 8,
  "tipo_imovel": "Casa",
  "horas_alto_consumo": 5
}
```

### Resposta moderada

```json
{
  "categoria": "Moderado",
  "probabilidade": 0.74,
  "recomendacoes": [
    "Reduzir parte do consumo em horários de pico",
    "Monitorar equipamentos usados por longos períodos",
    "Avaliar ajustes na rotina para distribuir melhor o consumo"
  ],
  "custo_estimado_mensal": 195.0
}
```

## Exemplo ineficiente

### Requisição ineficiente

```json
{
  "consumo_kwh": 520,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 14,
  "tipo_imovel": "Pequeno comércio",
  "horas_alto_consumo": 9
}
```

### Resposta ineficiente

```json
{
  "categoria": "Ineficiente",
  "probabilidade": 0.88,
  "recomendacoes": [
    "Reduzir o uso de equipamentos durante horários de pico",
    "Avaliar aparelhos com alto consumo energético",
    "Distribuir atividades de maior consumo ao longo do dia"
  ],
  "custo_estimado_mensal": 390.0
}
```

## Tratamento de erros

Os erros devem ser retornados em JSON e precisam ser documentados conforme implementação final do backend.

### Exemplo de erro

```json
{
  "erro": "Dados de entrada inválidos",
  "detalhes": ["consumo_kwh deve ser maior que zero"]
}
```

## Integração entre Backend e Data Science

Fluxo definido até o momento:

| Frente              | Responsabilidade                                                        |
| ------------------- | ----------------------------------------------------------------------- |
| Python/Data Science | Classificação energética e recomendações.                               |
| Backend             | Validação, custo estimado, orquestração, persistência e retorno da API. |
| Fallback local      | Classificação e recomendações quando a API Python falhar.               |

## Observações finais

- Este contrato representa a proposta inicial da documentação.
- O contrato final deve ser ajustado conforme implementação real do backend.
- O endpoint `POST /analise-energetica` deve permanecer documentado como requisito mínimo do MVP.
