# Padrão de Commits

O projeto **EnergiAI** utiliza a especificação **Conventional Commits** para padronizar o histórico de alterações no repositório. Isso facilita o rastreamento de mudanças, geração de logs e automações de build.

---

## Formato Principal

Cada commit realizado deve seguir a estrutura padrão abaixo:

```bash
<tipo>(<escopo>): <descricao>
```

> [!NOTE]
> O preenchimento do `<escopo>` é altamente recomendado para contextualizar qual parte do sistema foi alterada, mas é considerado opcional.

---

## Tipos de Commit Permitidos

| Tipo | Uso |
| :--- | :--- |
| `feat` | Implementação de novas funcionalidades (corresponde a `feature` no fluxo Git). |
| `fix` | Correção de bugs ou falhas. |
| `docs` | Alterações exclusivas em documentações (como README.md e arquivos md). |
| `style` | Alterações puramente estéticas ou de formatação que não alteram lógica (espaçamento, ponto e vírgula, linting). |
| `refactor` | Modificações no código que não adicionam funcionalidades ou corrigem bugs, mas melhoram a estrutura. |
| `perf` | Ajustes específicos para ganho de performance e desempenho. |
| `test` | Criação, modificação ou correção de testes automatizados. |
| `build` | Alterações que afetam o processo de build do projeto ou dependências externas (ex: arquivos Maven, Gradle, NPM). |
| `chore` | Manutenções gerais e arquivos utilitários que não alteram código de negócio (ex: configuração de ambiente local). |
| `ci` | Ajustes em arquivos de integração contínua e pipelines (ex: GitHub Actions workflows). |
| `revert` | Reversão de um ou mais commits anteriores. |
| `deps` | Atualização ou alteração de pacotes e dependências de terceiros. |

---

## Escopos Recomendados

Para ajudar na classificação do escopo do commit, utilize preferencialmente uma das seguintes definições (a lista não é fechada, mas cobre a maior parte das situações):

| Escopo | Área de Aplicação |
| :--- | :--- |
| `api` | Controladores, mapeamento de rotas e contratos de API REST. |
| `service` | Regras e lógica de negócio. |
| `validation` | Restrições e validações de dados de entrada. |
| `recommendation` | Lógica de sugestões e recomendações energéticas. |
| `classification` | Algoritmos e regras de classificação de consumo. |
| `database` | Entidades, mapeamento ORM, migrations e repositórios de banco de dados. |
| `docker` | Dockerfile, Docker Compose ou configurações de contêineres. |
| `swagger` | Documentação OpenAPI/Swagger. |
| `data` | Datasets, processamento de dados e engenharia de dados. |
| `oci` | Integrações com recursos da Oracle Cloud Infrastructure. |
| `docs` | Documentação técnica geral em Markdown. |
| `config` | Arquivos e classes de propriedades ou configurações do app. |
| `security` | Mecanismos de autenticação, autorização ou criptografia. |
| `tests` | Implementação e massa de testes. |

---

## Regras de Formatação

Ao redigir a mensagem do commit, atente-se às seguintes boas práticas:

1. **Letras Minúsculas**: Use letras minúsculas em toda a mensagem (tanto no tipo/escopo quanto na descrição).
2. **Sem Ponto Final**: Não termine a descrição do commit com ponto final (`.`).
3. **Verbo no Infinitivo ou Direto**: Escreva ações no infinitivo ou modo imperativo/direto (ex: `adicionar endpoint` ou `corrige validacao`).
4. **Limite de Tamanho**: A primeira linha do commit não deve ultrapassar **72 caracteres**.
5. **Espaçamento**: Sempre deixe um espaço simples após os dois-pontos (`:`).
6. **Evite Acentuação**: Dê preferência a termos sem acentos ou caracteres especiais para prevenir problemas de codificação em terminais (ex: `analise` em vez de `análise`).

---

## Exemplos Práticos

### Commits Corretos

```bash
feat(api): adicionar endpoint de analise energetica
feat(classification): implementar classificacao por consumo
fix(validation): impedir consumo kwh negativo
docs(readme): adicionar instrucoes de execucao local
refactor(service): simplificar regra de classificacao
test(service): adicionar testes da classificacao energetica
chore(docker): adicionar dockerfile da api
ci(github): adicionar validacao de build no pull request
```

### Commits Incorretos (Evite!)

* `Feat: Adicionei endpoint.` (Letra maiúscula, verbo no passado e com ponto final)
* `fix:arrumando bug` (Sem espaço após os dois-pontos, verbo no gerúndio)
* `update code` (Sem tipo ou escopo, descrição genérica)
* `alterações finais` (Genérico e com acentuação)
* `commit do lucas` (Não descreve a alteração e não segue padrão)
