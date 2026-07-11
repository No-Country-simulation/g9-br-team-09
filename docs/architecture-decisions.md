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
POST /api/v1/analise-energetica
```

### Motivo ADR-006

O backend expõe o controller em `/analise-energetica`, mas o contrato público final inclui o `context-path` global `/api/v1`.

### Impacto ADR-006

O contrato documental e os testes públicos devem referenciar `POST /api/v1/analise-energetica`.

---

## ADR-007 — Oracle Autonomous Database como serviço OCI principal

### Decisão ADR-007

Oracle Autonomous Database é o serviço OCI principal definido para persistência no ambiente cloud.

### Motivo ADR-007

Essa escolha atende ao requisito de uso de OCI e alinha a estratégia de persistência do projeto ao ambiente Oracle Cloud.

### Impacto ADR-007

A documentação deve tratar Oracle Autonomous Database como serviço cloud alvo, mantendo a necessidade de evidência técnica compatível com o estado real do projeto.

---

## ADR-008 — H2 para desenvolvimento local e testes

### Decisão ADR-008

H2 é utilizado como banco local/de desenvolvimento/testes conforme a configuração definida pelo projeto.

### Motivo ADR-008

O perfil local do backend já usa H2 em memória, o que simplifica desenvolvimento, testes e execução do projeto sem dependência imediata do ambiente cloud.

### Impacto ADR-008

Documentação, onboarding e troubleshooting devem diferenciar o banco local H2 da persistência-alvo em Oracle Autonomous Database.

---

## ADR-009 — Separação entre estado atual do backend e arquitetura-alvo com Data Science

### Decisão ADR-009

O estado atual do backend usa classificação local baseada em regras com `fonte_classificacao = RULE_BASED`.

A arquitetura-alvo prevê integração com Data Science para classificação via `ML_MODEL`, com possibilidade de `RULE_BASED_FALLBACK` em caso de erro, timeout ou resposta inválida.

### Motivo ADR-009

Os enums públicos já preveem `RULE_BASED`, `ML_MODEL` e `RULE_BASED_FALLBACK`, mas a integração com Data Science ainda não está implementada no backend atual.

### Impacto ADR-009

- O backend continua responsável pela API pública, validação, orquestração, cálculo de custo, persistência e resposta final.
- A documentação não deve apresentar a integração HTTP com Data Science como concluída.
- A responsabilidade final pelas recomendações em cenário `ML_MODEL` deve seguir o contrato de integração validado entre as frentes.
