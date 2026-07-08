# Uso de Issues

No projeto **EnergiAI**, a organização e a rastreabilidade do trabalho começam na criação e manutenção de **GitHub Issues**. Toda alteração no código deve estar associada a uma Issue aberta e documentada.

---

## Vinculação Obrigatória

* **Nenhuma alteração de código deve ser feita sem uma Issue correspondente.**
* Antes de iniciar qualquer desenvolvimento ou criar uma branch, verifique no painel de Issues do repositório se já existe um card relacionado à tarefa em questão.
* Se a Issue já existir, atribua a si mesmo (`Assignee`) e mova-a para a coluna de desenvolvimento em progresso.
* Se a tarefa ainda não possuir uma Issue, crie uma nova seguindo as diretrizes abaixo.

---

## Criando uma Nova Issue

Ao abrir uma nova Issue, certifique-se de preencher as seguintes informações essenciais:

1. **Título Objetivo**: Deve ser curto e descrever claramente a entrega (ex: *Criar endpoint de análise energética*).
2. **Descrição Completa**: Descreva detalhadamente o que precisa ser feito e qual o problema está sendo resolvido.
3. **Critérios de Aceite**: Uma lista de requisitos ou comportamentos esperados que devem ser atendidos para que a Issue seja considerada concluída.
4. **Área Impactada (Labels)**: Classifique a Issue usando as etiquetas apropriadas do projeto:
   * `backend`
   * `frontend`
   * `dados` (dataset, notebooks, etc.)
   * `infra` / `docker` / `oci`
   * `documentacao`
5. **Idioma**: As Issues devem ser escritas preferencialmente em português (PT-BR).

---

## Exemplo de Associação

O número gerado pela Issue após a sua criação será o identificador principal utilizado no nome da branch de desenvolvimento correspondente.

### Exemplo

1. **Issue criada no GitHub**:
   * Título: `#12 Criar endpoint de análise energética`
   * Número gerado: **12**

2. **Branch correspondente**:
   * Nome: `feature/12/criar-endpoint-analise-energetica`

Ao vincular a branch ao número da Issue, garantimos a integração automatizada e fácil rastreabilidade no histórico do GitHub.
