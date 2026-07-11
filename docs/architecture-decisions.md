# Decisões Arquiteturais — EnergIAI

Este documento registra decisões técnicas e organizacionais relevantes para o projeto EnergIAI.

As decisões abaixo devem ser revisadas pela equipe via Pull Request antes de serem consideradas definitivas.

## ADR-001 — Idioma da documentação

### Decisão ADR-001

A documentação do projeto será escrita em PT-BR.

### Motivo ADR-001

A equipe atua em português e a documentação precisa ser clara para todos os integrantes, avaliadores e participantes do hackathon.

### Impacto ADR-001

Arquivos como `README.md`, atas, status do projeto, contrato da API e documentação funcional devem priorizar PT-BR.

---

## ADR-002 — Contrato externo da API em PT-BR

### Decisão ADR-002

O contrato externo da API será documentado em PT-BR.

### Motivo ADR-002

O MVP precisa ser facilmente compreendido na entrega, documentação e vídeo demo.

### Impacto ADR-002

Campos de entrada, campos de saída, exemplos de request e response e mensagens funcionais devem seguir o padrão definido pela equipe.

---

## ADR-003 — Campos JSON em snake_case

### Decisão ADR-003

Os campos externos do JSON seguirão o padrão `snake_case`.

### Motivo ADR-003

O padrão facilita leitura, documentação e integração entre as frentes de backend, Data Science e eventuais consumidores da API.

### Exemplos ADR-003

- `consumo_kwh`
- `uso_horario_pico`
- `quantidade_equipamentos`
- `tipo_imovel`
- `horas_alto_consumo`
- `custo_estimado_mensal`

---

## ADR-004 — Código interno em inglês

### Decisão ADR-004

O código interno deverá usar nomes em inglês para classes, métodos, funções, variáveis e estruturas técnicas.

### Motivo ADR-004

Essa prática mantém consistência com convenções de Java, Spring Boot, bibliotecas, frameworks e exemplos técnicos usados no desenvolvimento.

### Impacto ADR-004

A documentação externa pode permanecer em PT-BR, mas o código deve manter padrão técnico em inglês.

---

## ADR-005 — Backend em Java/Spring Boot

### Decisão ADR-005

O backend será desenvolvido com Java e Spring Boot.

### Motivo ADR-005

A stack atende ao requisito de API REST e facilita organização de controllers, services, DTOs, validações, testes e documentação da API.

### Impacto ADR-005

O backend deve concentrar validação de entrada, cálculo de custo, orquestração do fluxo, persistência, fallback local e exposição dos endpoints REST.

---

## ADR-006 — Endpoint mínimo obrigatório

### Decisão ADR-006

O endpoint mínimo obrigatório do MVP será:

```http
POST /analise-energetica
```
