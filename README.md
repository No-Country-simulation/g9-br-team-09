# G9-BR-TEAM-09 — EnergIAI

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


## Funcionalidades obrigatórias

O MVP deve contemplar:

- Classificação do perfil energético em:
  - `Eficiente`
  - `Moderado`
  - `Ineficiente`
- Geração de recomendações de otimização energética;
- Estimativa financeira usando tarifa de referência de **R$ 0,75/kWh**;
- API REST com endpoint principal:
  - `POST /analise-energetica`
- Retorno em formato JSON;
- Modelo treinado e carregado corretamente;
- Integração com pelo menos um serviço OCI;
- Mínimo de 3 exemplos reais ou simulados de uso.

---

## Exemplo de entrada JSON

```json
{
  "consumo_kwh": 420,
  "uso_horario_pico": true,
  "quantidade_equipamentos": 10,
  "tipo_imovel": "Casa",
  "horas_alto_consumo": 8
}