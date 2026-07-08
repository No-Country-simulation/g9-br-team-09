# Padrões de Desenvolvimento - EnergiAI

Este documento define os padrões de Git, GitHub e processos de entrega adotados no projeto **EnergiAI**. O objetivo é manter o repositório limpo, organizado, produtivo e com histórico de alterações rastreável.

---

## ⚡ Guia de Referência Rápida (Quick Reference)

Caso precise consultar rapidamente a estrutura padrão para o fluxo de trabalho:

| Elemento | Padrão Recomendado | Exemplo Real |
| :--- | :--- | :--- |
| **Branch** | `<tipo>/<issue-id>/<descricao>` | `feature/12/criar-endpoint-analise-energetica` |
| **Commit** | `<tipo>(<escopo>): <descricao>` | `feat(api): adicionar endpoint de analise energetica` |
| **Título do PR** | `<tipo>(<escopo>): <descricao> (#<issue-id>)` | `feat(api): adicionar endpoint de analise energetica (#12)` |
| **Corpo do PR** | Vinculação direta da Issue | `Closes #12` |
| **Estratégia de Merge** | **Squash Merge** | Todos os commits intermediários são consolidados em um commit com o título do PR. |

---

## 📖 Documentações Detalhadas

Para compreender os detalhes, boas práticas e comandos de terminal recomendados para cada etapa do ciclo de desenvolvimento, acesse os subdocumentos específicos abaixo:

1. 🔄 **[Fluxo Git](development-patterns/git-workflow.md)**
   * Estrutura de branches principais (`main` e `develop`).
   * Como criar branches de trabalho e atalhos úteis.
   * Fluxo de correção emergencial (`hotfixes`).
   * Limpeza de branches e referências obsoletas.

2. 📋 **[Uso de Issues](development-patterns/issues.md)**
   * A obrigatoriedade de vinculação a uma Issue.
   * Regras para abertura de Issues (critérios de aceite, labels e áreas).
   * Exemplo prático de fluxo integrado.

3. 🌿 **[Padrão de Branches](development-patterns/branches.md)**
   * Estrutura de nomenclatura obrigatória.
   * Tipos permitidos (`feature`, `fix`, `docs`, `test`, `refactor`, `chore`, `hotfix`).
   * Validações, restrições (acentos, maiúsculas, hifens) e exemplos.

4. 💾 **[Padrão de Commits](development-patterns/commits.md)**
   * Uso da especificação **Conventional Commits**.
   * Lista completa de tipos de commits (`feat`, `fix`, `refactor`, etc.).
   * Escopos recomendados da arquitetura.
   * Restrições de formatação e exemplos de mensagens corretas e incorretas.

5. 🔀 **[Pull Requests e Squash Merge](development-patterns/pull-requests.md)**
   * Regras de abertura de PRs para `develop`.
   * Template de descrição recomendado.
   * O conceito e funcionamento do Squash Merge.
   * Mapa mental da relação lógica e do fluxo de ponta a ponta.

---

## 🛠️ Validação Automática

> [!NOTE]
> Enquanto validações automáticas por meio de pipelines de CI (GitHub Actions) não estiverem ativas, é dever de todo desenvolvedor seguir esses padrões manualmente e de todo revisor auditar esses formatos durante a aprovação do Pull Request.
