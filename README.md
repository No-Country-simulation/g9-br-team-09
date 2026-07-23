# G9-BR-TEAM-09 — EnergiAI

## Inteligência para Consumo Energético

MVP desenvolvido pela equipe **G9-BR-TEAM-09** no Hackathon ONE G9-BR / Alura + Oracle / NoCountry.

O projeto **EnergIAI** tem como objetivo analisar dados de consumo de energia elétrica, classificar o perfil energético de uma residência ou pequeno estabelecimento, gerar recomendações de otimização e estimar o custo mensal com base em uma tarifa de referência.

---

## Problema

Muitas pessoas e pequenos negócios recebem contas de energia elevadas, mas têm pouca clareza sobre quais hábitos, horários ou equipamentos mais impactam o consumo.

A proposta do EnergIAI é transformar dados simples de consumo em informações úteis para apoiar decisões mais conscientes, econômicas e sustentáveis.

---

## Objetivo do MVP

Criar uma solução funcional capaz de:

- Analisar padrões de consumo energético;
- Classificar o perfil energético em categorias;
- Gerar recomendações de melhoria;
- Estimar o custo mensal de energia;
- Disponibilizar o resultado em formato JSON via API REST;
- Utilizar pelo menos um serviço da Oracle Cloud Infrastructure — OCI.

---

## Estrutura do Repositório

```text
backend/       -> API Java + Flyway migrations
frontend/      -> interface web
data-science/  -> notebook, dataset, modelo e API Python
infra/         -> Docker, OCI, scripts e deploy
docs/          -> documentação do projeto
```

---

## Documentação do Projeto

A documentação principal do projeto está organizada na pasta `docs/`.

| Documento                                                     | Descrição                                                                    |
| ------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| [`project-status.md`](docs/project-status.md)                 | Status geral do projeto, responsáveis, pendências, riscos e próximos passos. |
| [`api-contract.md`](docs/api-contract.md)                     | Contrato inicial da API, endpoint obrigatório e exemplos de uso.             |
| [`architecture-decisions.md`](docs/architecture-decisions.md) | Decisões técnicas e organizacionais registradas durante o projeto.           |
| [`meetings.md`](docs/meetings.md)                             | Atas e registros das principais reuniões da equipe.                          |

---

## Funcionalidades obrigatórias

O MVP deve contemplar:

- Classificação do perfil energético em:
  - `EFICIENTE`
  - `MODERADO`
  - `INEFICIENTE`
- Geração de recomendações de otimização energética;
- Estimativa financeira usando tarifa de referência de **R$ 0,75/kWh**;
- API REST com endpoint principal:
  - `POST /api/v1/analise-energetica`
- Retorno em formato JSON;
- Modelo treinado e carregado corretamente;
- Integração com pelo menos um serviço OCI;
- Mínimo de 3 exemplos reais ou simulados de uso.

---

## Configuração da tarifa

A tarifa padrão usada no cálculo financeiro fica em `backend/src/main/resources/application.properties`.

Fórmula aplicada:

`custoEstimadoMensal = consumoKwh * tarifaKwh`

Valor padrão atual:

`energy.tariff.default=0.75`

Para alterar a tarifa de referência do MVP, ajuste essa propriedade.

---

## Integração com a API de Machine Learning

O backend utiliza uma estratégia ML-first para realizar a análise energética.

O fluxo principal é:

```text
Backend
→ POST /predict na API Python
→ resposta válida
→ utiliza classificação e recomendações do modelo
→ fonte_classificacao = ML_MODEL
```

Quando a API Python estiver indisponível ou retornar uma resposta inválida, o backend utiliza a classificação e as recomendações locais:

```text
Backend
→ falha ou resposta inválida da API Python
→ classificador local
→ recomendações locais
→ fonte_classificacao = RULE_BASED_FALLBACK
```

O endpoint público continua retornando uma análise válida quando o fallback pode ser executado.

Por padrão, a API Python é esperada em `http://localhost:8000`. As propriedades podem ser configuradas por variáveis de ambiente:

| Variável                 | Finalidade                                         | Valor padrão            |
| ------------------------ | -------------------------------------------------- | ----------------------- |
| `ML_API_BASE_URL`        | URL-base da API Python usada pelo `POST /predict`. | `http://localhost:8000` |
| `ML_API_CONNECT_TIMEOUT` | Tempo máximo para estabelecer a conexão HTTP.      | `2s`                    |
| `ML_API_READ_TIMEOUT`    | Tempo máximo para aguardar a resposta HTTP.        | `5s`                    |

Exemplo de execução local com valores personalizados:

```bash
cd backend
ML_API_BASE_URL=http://localhost:8000 ML_API_CONNECT_TIMEOUT=2s ML_API_READ_TIMEOUT=5s ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Exemplo de entrada JSON

```json
{
  "consumo_kwh": 420,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 10,
  "tipo_imovel": "CASA",
  "horas_alto_consumo": 8
}
```

## Executar localmente

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

O backend utiliza H2 em memória no profile `local`. Os dados são perdidos quando a aplicação é encerrada.

O schema de domínio é gerenciado exclusivamente pelo Flyway; a migration inicial `V1` já cria a tabela `energy_analysis`.

Para execução no Oracle Autonomous Database, o profile `oci` usa JDBC Thin com TLS sem wallet. Consulte o [guia operacional do Oracle Autonomous Database](docs/oracle-autonomous-database.md).

Durante a validação real no Oracle, a migration e os mapeamentos JPA foram alinhados aos tipos nativos do banco. Os DTOs, o contrato JSON e as regras de negócio permaneceram inalterados.

O profile `local` é independente do Oracle Autonomous Database e não requer `.env`, `oci.env`, `DB_URL`, `DB_USERNAME` ou `DB_PASSWORD`.

As credenciais externas são necessárias somente ao executar o backend com o profile `oci`. Caso o profile `oci` seja ativado sem essas variáveis, a aplicação não conseguirá configurar o datasource e falhará na inicialização.

### Health check operacional

O backend expõe exclusivamente o health check operacional em `GET /api/v1/actuator/health`. Quando saudável, ele retorna:

```json
{
  "status": "UP"
}
```

Detalhes internos permanecem ocultos e os demais endpoints do Actuator não são expostos. Valide localmente com:

```bash
curl --fail http://localhost:8080/api/v1/actuator/health
```

### Swagger

```text
http://localhost:8080/api/v1/swagger-ui/index.html
```

### H2 Console

```text
http://localhost:8080/api/v1/h2-console
```

### Credenciais do H2

```text
JDBC URL: jdbc:h2:mem:energiai
User Name: sa
Password: vazio
```
