# Fluxo Git

Este documento descreve o fluxo de trabalho Git adotado no projeto **EnergiAI**, detalhando as ramificações (branches) utilizadas, a criação de branches de funcionalidade, fluxo de hotfixes e rotinas recomendadas para manter o repositório limpo.

---

## Estrutura de Branches

O projeto utiliza o seguinte fluxo principal de branches:

* `main`: Branch estável, contendo apenas código testado e pronto para produção (entrega ou deploy).
* `develop`: Branch principal de desenvolvimento, onde as novas funcionalidades são integradas para testes de integração.
* **Branches Temporárias**: Criadas para tarefas específicas (novas features, correções, refatorações, documentação, testes, etc.).

Todo desenvolvimento regular deve iniciar a partir da branch `develop`. A única exceção são correções críticas de produção (hotfixes), que partem diretamente da `main`.

---

## Criando uma Branch de Desenvolvimento

Para iniciar o desenvolvimento de uma tarefa (sempre associada a uma Issue):

1. **Atualize sua develop local**:
   ```bash
   git checkout develop
   git pull origin develop
   ```

2. **Crie a branch temporária**:
   ```bash
   git checkout -b feature/12/criar-endpoint-analise-energetica
   ```

3. **Envie as modificações realizadas**:
   ```bash
   git add .
   git commit -m "feat(api): adicionar endpoint de analise energetica"
   git push origin feature/12/criar-endpoint-analise-energetica
   ```

> [!TIP]
> **Mantenha sua branch sincronizada:** Durante o desenvolvimento, se outras branches forem mescladas à `develop`, atualize a sua branch local executando `git merge develop` ou `git rebase develop`. Isso reduz a probabilidade de conflitos no Pull Request.

---

## Fluxo para Hotfix

Hotfixes são correções críticas urgentes aplicadas diretamente sobre o ambiente de produção (`main`).

1. **Crie a branch a partir da main**:
   ```bash
   git checkout main
   git pull origin main
   git checkout -b hotfix/15/corrigir-erro-critico-producao
   ```

2. **Após realizar a correção**:
   ```bash
   git add .
   git commit -m "fix(api): corrigir erro critico na analise"
   git push origin hotfix/15/corrigir-erro-critico-producao
   ```

3. **Integração**:
   * Abra o Pull Request apontando diretamente para a `main`.
   * **Importante:** Após aprovação e merge do PR na `main`, traga essa correção de volta para a `develop`. Você pode abrir um PR de `main` para `develop` ou fazer o merge localmente e enviar.

---

## Recomendações para o Time

### Antes de iniciar qualquer tarefa
Sempre sincronize a branch `develop` local:
```bash
git checkout develop
git pull origin develop
```

### Antes de abrir um Pull Request
Verifique o estado das suas alterações e os últimos commits locais para garantir que a mensagem de commit está correta:
```bash
git status
git log --oneline -5
```

### Limpeza de branches locais antigas
Após a aprovação e mesclagem do seu Pull Request, atualize seu repositório local e limpe as branches que já foram integradas:
```bash
git checkout develop
git pull origin develop

# Lista branches locais já mescladas na develop
git branch --merged develop

# Exclui a branch local mesclada
git branch -d nome-da-branch
```

### Limpeza de referências remotas inexistentes
Para remover do seu ambiente local as referências a branches que já foram apagadas no repositório do GitHub:
```bash
git fetch --prune
```
