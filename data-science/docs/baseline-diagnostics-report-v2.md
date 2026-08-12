# Relatório de diagnósticos da baseline — Dataset EnergiAI V2

## Escopo

Este relatório registra diagnósticos reproduzíveis executados sobre a baseline sintética atual.

Os resultados não constituem evidência causal, validade externa ou desempenho em dados reais.

## Configuração reproduzível

| Parâmetro | Valor |
| --- | ---: |
| Tamanho da amostra | `5000` |
| Seed | `42` |
| Repetições da permutação | `10` |

Comando:

```powershell
python data-science/src/baseline_diagnostics_report.py --sample-size 5000 --seed 42 --n-repeats 10 --output "data-science/docs/baseline-diagnostics-report-v2.md"
```

## Benchmark diagnóstico

| Modelo | F1-macro |
| --- | ---: |
| Dummy | 0,189112 |
| Regressão Logística | 0,937864 |
| Árvore de Decisão | 0,836870 |

O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.

## Diagnóstico por feature individual

| Feature | F1-macro |
| --- | ---: |
| `consumo_kwh` | 0,602815 |
| `uso_horario_pico` | 0,422359 |
| `quantidade_equipamentos` | 0,521173 |
| `tipo_imovel` | 0,189112 |
| `horas_alto_consumo` | 0,586904 |

O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.

## Diagnóstico por ablação

| Feature removida | F1-macro | Queda absoluta |
| --- | ---: | ---: |
| `consumo_kwh` | 0,904948 | 0,032916 |
| `uso_horario_pico` | 0,906765 | 0,031100 |
| `quantidade_equipamentos` | 0,886148 | 0,051717 |
| `tipo_imovel` | 0,745748 | 0,192117 |
| `horas_alto_consumo` | 0,887395 | 0,050469 |

O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.

## Permutation importance

| Feature | Importância média | Desvio-padrão |
| --- | ---: | ---: |
| `consumo_kwh` | 0,323775 | 0,014760 |
| `uso_horario_pico` | 0,110026 | 0,009497 |
| `quantidade_equipamentos` | 0,209891 | 0,011717 |
| `tipo_imovel` | 0,298839 | 0,012471 |
| `horas_alto_consumo` | 0,280542 | 0,013937 |

O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.

## Consolidação do guardrail

| Critério | Resultado |
| --- | ---: |
| Modelo completo | 0,937864 |
| Melhor feature individual | `consumo_kwh` — 0,602815 |
| Relação entre o melhor resultado individual e o completo | 64,3% |
| Limite inicial | 95,0% |
| Situação | **ATENDIDO** |

O guardrail avalia somente a dependência individual das features na baseline sintética atual.

O resultado mede a capacidade do modelo de reproduzir padrões da base sintética sob as condições testadas.

## Limitações

- O dataset avaliado é sintético.
- O target da baseline atual é determinístico.
- As métricas refletem as condições específicas desta execução.
- Nenhum resultado deve ser apresentado como desempenho real.
