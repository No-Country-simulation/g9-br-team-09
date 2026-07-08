# Padrão de Branches

Este documento detalha o padrão de nomenclatura obrigatório para a criação de ramificações (branches) no repositório **EnergiAI**.

---

## Formato Obrigatório

Toda branch temporária ou de desenvolvimento deve seguir rigorosamente a estrutura abaixo:

```bash
<tipo>/<numero-da-issue>/<descricao-curta>
```

### Significado dos Parâmetros

* `<tipo>`: A natureza da alteração que está sendo feita (veja a tabela abaixo).
* `<numero-da-issue>`: O número da Issue associada no GitHub (apenas os dígitos).
* `<descricao-curta>`: Resumo do que está sendo implementado, separado por hifens.

---

## Tipos Permitidos

| Tipo | Uso Recomendado |
| :--- | :--- |
| `feature` | Implementação de novas funcionalidades. |
| `fix` | Correção de bugs. |
| `docs` | Alterações e criação de documentação. |
| `test` | Escrita, ajuste ou manutenção de testes de software. |
| `refactor` | Refatoração de código que não altera o comportamento externo do sistema. |
| `chore` | Tarefas administrativas, atualizações de dependências, Docker, build pipelines ou configurações de ferramentas. |
| `hotfix` | Correções críticas emergenciais de produção, criadas obrigatoriamente a partir da branch `main`. |

---

## Regras de Nomenclatura e Formatação

Para evitar problemas em ambientes Git e manter a padronização do histórico, atente-se às seguintes restrições:

* **Letras Minúsculas**: Use exclusivamente caracteres minúsculos (`a-z`). Não use letras maiúsculas.
* **Separadores**: Use o caractere hífen (`-`) para separar palavras na descrição curta. Não utilize espaços, underscores (`_`) ou barras (`/`) fora da separação dos blocos.
* **Caracteres Especiais e Acentuação**: Não utilize acentos (ex: `á`, `õ`, `ç`) nem símbolos especiais (ex: `@`, `!`, `$`, `#`).
* **Numeração**: O número presente na branch deve corresponder exatamente ao identificador da Issue cadastrada no GitHub.

---

## Exemplos Práticos

### Branches Válidas e Corretas

```bash
feature/1/criar-endpoint-analise-energetica
feature/2/implementar-classificacao-consumo
fix/5/corrigir-validacao-consumo-kwh
docs/7/atualizar-readme
test/9/adicionar-testes-service
refactor/11/refatorar-service-classificacao
chore/13/configurar-docker
hotfix/15/corrigir-erro-critico-producao
```

### Branches Inválidas (Evite!)

* `feat/1/criar-endpoint` (usa `feat` em vez do tipo completo `feature` na branch)
* `feature/issue-1/criar-endpoint` (contém texto extra `issue-` na numeração)
* `feature/endpoint-analise` (não possui o número da Issue vinculada)
* `Feature/1/AnaliseEnergetica` (utiliza letras maiúsculas)
* `feature/1/criar endpoint` (contém espaço em branco)
* `feature/1/criar-endpoint-análise` (contém caractere acentuado `í`)
