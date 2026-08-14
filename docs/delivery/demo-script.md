# Roteiro de apresentação — EnergiAI

**Demo Day:** 18/08
**Apresentador:** Fabio
**Apoio técnico e manutenção do roteiro:** Lucas
**Tempo-alvo:** aproximadamente 4min35, abaixo do limite de 5 minutos.

Os tempos são referências de ensaio. Se a resposta da análise carregar antes,
avance normalmente; não prolongue o preenchimento apenas para cumprir o
cronômetro.

## Preparação antes de começar

- Deixe a aplicação aberta em uma conta de demonstração já autenticada, na tela
  de nova análise.
- Deixe o vídeo de plano B pronto no ponto inicial da demonstração.
- Não execute cadastro ou login durante o pitch; não abra terminal, consoles ou
  dashboards de infraestrutura.

## Roteiro cronometrado

### 0:00–0:30 — Problema

**Fabio fala**

> Uma conta de energia informa consumo e custo, mas nem sempre ajuda a entender
> quais características daquele padrão de uso podem estar relacionadas ao
> resultado. O EnergiAI foi criado para dar esse ponto de partida de forma
> simples.

**Fabio mostra**

- Tela inicial da análise, sem entrar em detalhes técnicos.

**Transição**

> Em vez de apenas exibir um número, a proposta é transformar dados de consumo
> em informações que sejam mais fáceis de interpretar.

### 0:30–0:55 — Proposta de valor

**Fabio fala**

> O usuário informa consumo mensal, uso em horário de pico, quantidade de
> equipamentos, tipo de imóvel e horas de alto consumo. O EnergiAI transforma
> isso em categoria energética, Índice de Ineficiência, probabilidade,
> estimativa de custo e recomendações.

**Fabio mostra**

- Os cinco campos do formulário, sem navegar para a arquitetura.

**Transição**

> Vou mostrar esse fluxo com um cenário doméstico plausível.

### 0:55–1:55 — Demo: preenchimento e envio

**Fabio mostra e clica**

1. Preencha `420` em consumo mensal.
2. Marque uso em horário de pico.
3. Informe `10` equipamentos.
4. Selecione `CASA`.
5. Informe `8` horas de alto consumo.
6. Envie a análise.

**Fabio fala enquanto preenche**

> Neste exemplo, temos uma casa com consumo de 420 kWh por mês, uso em horário
> de pico, dez equipamentos e oito horas de alto consumo. São informações que
> uma pessoa consegue reconhecer no próprio contexto de consumo.

**Transição**

> Com esses dados enviados, o resultado reúne a classificação e os elementos
> que ajudam a interpretá-la.

### 1:55–2:30 — Resultado

**Fabio mostra**

- A tela de resultado, apontando primeiro para a categoria.
- Em seguida, o Índice de Ineficiência, a probabilidade e o custo.

**Fabio fala**

> A categoria exibida é **[LER CATEGORIA DA TELA]**. O Índice de Ineficiência
> está em **[LER ÍNDICE DA TELA]**: ele indica a severidade do padrão identificado
> na escala apresentada pela aplicação, e não é a mesma coisa que probabilidade.
> A probabilidade observada é **[LER PROBABILIDADE DA TELA]** e acompanha a
> classificação apresentada. Ela é um indicador do resultado, não uma garantia.

> Para este cenário, a tarifa de referência do MVP é R$ 0,75 por kWh, resultando
> em um custo estimado de R$ 315,00.

**Transição**

> Além de classificar, a aplicação procura indicar ações relacionadas ao cenário
> informado.

### 2:30–2:50 — Recomendações

**Fabio mostra e clica**

- A área de recomendações.
- Escolha uma recomendação clara que esteja visível na tela; não leia todas.

**Fabio fala**

> Como exemplo, a recomendação destaca **[LER UMA RECOMENDAÇÃO DA TELA]**. A
> proposta não é entregar somente uma classificação, mas orientar a leitura dos
> dados que foram informados.

**Transição**

> E essa análise não fica solta: ela pertence à conta que a criou.

### 2:50–3:10 — Conta e histórico individual

**Fabio mostra e clica**

- Abra rapidamente o histórico ou o painel da conta já autenticada.

**Fabio fala**

> O EnergiAI possui cadastro e login porque cada análise fica associada à conta
> que a criou. Assim, cada usuário visualiza somente o próprio histórico, seus
> detalhes e os dados consolidados no painel. Isso dá continuidade às análises e
> mantém o isolamento entre contas.

**Transição**

> Depois de ver a experiência funcionando, vale resumir o que sustenta esse
> fluxo.

### 3:10–3:35 — Arquitetura em alto nível

**Fabio mostra**

- O diagrama de [arquitetura](architecture.md), em tela simples.

**Fabio fala**

> A interface publicada na Vercel conversa somente com nosso backend Spring
> Boot, que está na Oracle Cloud. Esse backend centraliza as regras, a segurança
> e a persistência, consulta nossa API de Machine Learning para fazer a
> classificação e salva as análises no Oracle Autonomous Database.

**Transição**

> A parte de Machine Learning entra exatamente no momento dessa inferência.

### 3:35–4:00 — Machine Learning e limite do resultado

**Fabio fala**

> O modelo foi treinado com uma base sintética de 5.000 registros. Comparamos
> diferentes algoritmos e a solução final foi Random Forest com calibração
> isotônica. Ela alcançou aproximadamente 96,1% de F1-macro no holdout oficial
> da base sintética. Esse resultado mede o desempenho nas condições dessa base;
> não comprova desempenho equivalente no mundo real.

**Fabio mostra**

- Mantenha o diagrama ou um slide curto de apoio; não abra notebook, relatório
  completo ou hiperparâmetros.

**Transição**

> Também consideramos o que acontece quando esse serviço externo não está
> disponível.

### 4:00–4:20 — Resiliência

**Fabio fala**

> Normalmente, a classificação utiliza o serviço de Machine Learning. Se ele
> estiver temporariamente indisponível, o backend usa uma classificação de
> contingência baseada em regras para manter o fluxo disponível. Esse cenário
> foi validado de forma controlada e, após a restauração, o sistema voltou a usar
> Machine Learning.

**Fabio mostra**

- Não provoque fallback ao vivo; mantenha a tela atual ou o diagrama.

### 4:20–4:35 — Encerramento

**Fabio fala**

> O EnergiAI transforma dados simples de consumo em informações mais
> compreensíveis, com classificação, contexto e recomendações. É um ponto de
> partida para decisões de consumo mais conscientes. Obrigado.

## Plano B: vídeo

Se a demo ao vivo falhar, não diagnostique produção durante o pitch, não abra
terminal e não tente reiniciar FastAPI ou OCI. Fabio deve dizer:

> Para preservar o tempo e mostrar o fluxo validado, vamos seguir com o registro
> da demonstração.

Inicie o vídeo no ponto correspondente da narrativa e continue a explicação a
partir dali; não reinicie o pitch inteiro.

## O que não mostrar no pitch principal

Não é necessário mostrar nos cinco minutos:

- Swagger, GitHub Actions, console OCI, dashboard do Render ou console Oracle;
- código, terminal, notebook, logs ou hiperparâmetros.

Esses materiais servem como apoio para perguntas técnicas, não para a narrativa
principal.

## Perguntas prováveis

**Onde Oracle foi utilizado?**
Usamos a Oracle Cloud em duas partes principais: o backend e o Caddy executam
em uma instância OCI Compute, e a persistência das análises utiliza Oracle
Autonomous Database, com o schema gerenciado por Flyway.

**Onde entra Machine Learning?**
O Spring Boot consulta a FastAPI no Render, que executa o modelo para produzir a
inferência.

**O frontend chama o modelo diretamente?**
Não. O frontend chama somente a API Spring Boot; o backend orquestra a chamada
interna para a FastAPI.

**O que acontece se o serviço de ML cair?**
O backend usa uma classificação de contingência baseada em regras, mantendo a
análise disponível. Ela não é equivalente ao modelo de Machine Learning.

**O que significa F1-macro?**
É a média do F1 das classes, dando o mesmo peso a cada uma delas. O valor de
aproximadamente 96,1% foi obtido no holdout da base sintética.

**O dataset é real?**
Não. É uma base sintética de 5.000 registros; por isso o resultado não comprova
desempenho equivalente em dados do mundo real.

**Por que a tarifa é R$ 0,75 por kWh?**
É uma tarifa de referência do MVP usada para a estimativa de custo, não uma
tarifa universal.

**O que representa o Índice de Ineficiência?**
É o indicador de severidade do padrão identificado, apresentado pela interface
em escala de 0 a 100; ele não substitui a categoria nem a probabilidade.

**Como o histórico é isolado por usuário?**
O backend associa cada análise à conta autenticada e restringe histórico,
detalhes e dados consolidados aos registros daquele usuário.
