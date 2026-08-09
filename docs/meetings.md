# Reuniões e Atas — EnergiAI

Este documento registra as principais reuniões, alinhamentos e decisões da equipe G9-BR-Team 09 durante o desenvolvimento do projeto EnergiAI.

## 09/07/2026 — Reunião de alinhamento da equipe

### Contexto da reunião de 09/07

Reunião realizada para alinhar a organização inicial do projeto, responsabilidades da equipe, decisões técnicas, documentação e próximos passos do MVP.

### Participantes da reunião de 09/07

- Fábio
- Alan
- Adriana
- Gustavo
- Lucas
- Rafaela
- Túlio
- Miguel

### Decisões registradas em 09/07

- Fábio foi escolhido como representante/líder da equipe por unanimidade.
- A documentação do projeto será feita em PT-BR.
- O contrato externo da API será em PT-BR.
- Os campos do JSON seguirão o padrão `snake_case`.
- O código interno seguirá nomes em inglês.
- O backend será desenvolvido em Java/Spring Boot.
- A tarifa de referência para cálculo de custo será R$ 0,75/kWh.
- O serviço OCI principal definido foi Oracle Autonomous Database.
- O banco local/de testes definido foi H2.
- A frente de Data Science será conduzida principalmente por Túlio, com apoio de Miguel e Fábio.
- A frente de backend será conduzida por Gustavo, Lucas, Rafaela, Adriana e Alan.
- A documentação, requisitos, status do MVP, atas e organização de evidências ficarão sob responsabilidade principal de Fábio.

### Consolidação posterior do contrato público

- O contrato público implementado no backend foi consolidado como `POST /api/v1/analise-energetica`.
- O prefixo `/api/v1` faz parte do contrato público porque está centralizado no `server.servlet.context-path`.
- Os exemplos externos do contrato usam `snake_case`, enums em caixa alta e incluem `score` e `fonte_classificacao` na resposta oficial.

### Alinhamento entre backend e Data Science em 09/07

Fluxo definido até o momento:

- Python/Data Science será a fonte principal para classificação energética e recomendações na arquitetura-alvo.
- Backend será responsável por validação da entrada, cálculo de custo estimado, orquestração, persistência e retorno da API.
- Backend deverá possuir fallback local caso a API Python esteja indisponível ou retorne resposta inválida.
- No estado atual do backend, a classificação é executada localmente com `RULE_BASED`.
- A integração com Data Science permanece como arquitetura-alvo.
- A responsabilidade final pela geração de recomendações na integração futura depende do contrato definido entre as frentes.

### Pendências registradas em 09/07

- Confirmar contrato final entre backend e Data Science.
- Consolidar dataset inicial.
- Registrar critérios de classificação energética.
- Definir modelo simples para o MVP.
- Implementar e documentar a integração HTTP com Data Science.
- Registrar evidência de uso do Oracle Autonomous Database.
- Abrir Pull Request para revisão da documentação antes de merge.

### Observações sobre a reunião de 09/07

Esta ata registra o entendimento inicial da reunião e deve ser revisada pela equipe via Pull Request antes de ser considerada definitiva.

---

## 13/07/2026 — Sprint Meet Semana 1

### Contexto da reunião de 13/07

Reunião realizada para alinhar o andamento da Semana 1 do projeto EnergiAI, revisar o status do backend, identificar pendências de Data Science, discutir próximos passos de OCI e preparar a equipe para a Sprint Demo interna da semana.

### Participantes da reunião de 13/07

- Fábio
- Alan
- Gustavo
- Lucas
- Adriana — via chat

### Alinhamentos registrados em 13/07

- A Semana 0 foi considerada praticamente concluída pela equipe.
- O backend avançou além do esperado para o início da Semana 1.
- A documentação geral do projeto já foi organizada e mergeada em `develop`.
- A migration da tabela `energy_analysis` foi incorporada à base atual do projeto.
- O contrato público da API permanece centralizado em `POST /api/v1/analise-energetica`.
- A integração com Data Science segue como arquitetura-alvo.
- A equipe aguarda o notebook, base ou modelo inicial de Data Science para iniciar testes de integração.
- A API Python foi citada como caminho provável para o backend Java consumir a classificação do modelo.
- As recomendações devem ser tratadas como responsabilidade principal da frente de Data Science na arquitetura-alvo.
- O backend deve manter fallback local para garantir funcionamento do MVP caso a integração externa falhe.
- Oracle Autonomous Database segue como serviço OCI principal previsto, ainda dependendo de validação técnica e evidência real.
- A Sprint Demo de quinta-feira será usada para demonstrar o andamento interno da sprint.
- As entregas da plataforma podem ser atualizadas progressivamente até a Semana 5.
- Foi reforçado que a documentação deve registrar apenas informações confirmadas e não apresentar integrações futuras como concluídas.

### Pendências identificadas em 13/07

- Confirmar entrega do notebook, base ou modelo inicial de Data Science.
- Confirmar atualização da frente de Data Science sobre dataset, EDA, critérios, modelo e métricas.
- Definir o formato final da integração entre backend Java e Data Science.
- Implementar e validar a integração HTTP com a API Python, caso esse caminho seja confirmado.
- Registrar evidência técnica real do uso do Oracle Autonomous Database.
- Definir o que será apresentado na Sprint Demo interna da Semana 1.
- Atualizar entregáveis da plataforma conforme avanço real do projeto.

### Próximos passos definidos em 13/07

- Acompanhar a entrega do notebook de Data Science.
- Manter o acompanhamento dos PRs e issues do backend.
- Validar o estado real da integração com OCI antes de documentar como concluída.
- Preparar um resumo objetivo do andamento para a Sprint Demo.
- Atualizar a documentação apenas com informações confirmadas.
- Manter a transcrição completa apenas como fonte interna de apoio, sem publicá-la no repositório.

### Observações sobre a reunião de 13/07

Esta ata registra apenas os pontos operacionais da reunião. A transcrição completa não deve ser publicada no repositório por conter conversas pessoais e trechos informais que não fazem parte da documentação oficial do projeto.

---

## 16/07/2026 — Sprint Demo Semana 1

### Contexto da reunião de 16/07

Sprint Demo realizada para revisar os avanços da Semana 1, identificar bloqueios entre as frentes, alinhar o próximo passo da integração entre Backend e Data Science e definir atividades que podem avançar enquanto o contrato de inferência ainda está pendente.

### Participantes da reunião de 16/07

- Fábio
- Gustavo
- Rafaela
- Adriana — participação via chat

### Ausências registradas em 16/07

- Lucas informou previamente que não poderia participar da reunião.
- Alan, Túlio e Miguel não participaram, e o motivo das ausências não foi informado.

### Avanços apresentados em 16/07

- O backend foi considerado avançado para a Semana 1, com o fluxo mínimo de análise já implementado localmente.
- O contrato público atual do backend permanece documentado e pode ser adaptado após a consolidação do contrato com Data Science.
- O notebook inicial de Data Science está versionado e serve como ponto de partida para a evolução da solução.
- A equipe reconheceu que as variáveis de estudo do notebook não devem ser automaticamente tratadas como campos obrigatórios para o usuário.
- Rafaela informou que iniciará a elaboração de um protótipo do frontend no Figma para validação da equipe.
- A área de recursos da plataforma NoCountry foi preenchida com documentação e links já confirmados.
- A tarefa de ferramentas da equipe foi atualizada na plataforma com tecnologias efetivamente utilizadas.

### Alinhamentos sobre Backend e Data Science em 16/07

- O principal bloqueio identificado é a ausência de um contrato estável entre o modelo de Data Science e a API Python prevista para integração.
- A equipe precisa definir quais variáveis serão obrigatórias na inferência, quais poderão ser derivadas ou estimadas e quais ficarão para evolução futura.
- O backend consegue adaptar seus DTOs e a integração após a definição dos campos e da resposta esperada da API Python.
- O notebook atual deve ser tratado como artefato inicial de estudo e modelagem, não como contrato definitivo de integração.
- Alterações nas variáveis do modelo devem ser discutidas entre Data Science e Backend antes da implementação, para evitar retrabalho.
- A frente de Data Science precisa compartilhar dúvidas, bloqueios e tarefas entre Túlio, Miguel e o apoio de Fábio.
- A participação dos responsáveis de Data Science nas reuniões de alinhamento é necessária para que decisões técnicas não dependam apenas de mensagens assíncronas no Discord.

### Estado de OCI em 16/07

- Oracle Autonomous Database permanece como serviço OCI principal previsto para persistência no ambiente de demonstração.
- A integração real com OCI ainda precisa ser implementada, testada e comprovada tecnicamente.
- A documentação não deve apresentar OCI como concluída antes da existência de conexão, migrations, persistência e evidência técnica.

### Estado do frontend em 16/07

- O frontend permanece opcional para o MVP, mas pode avançar sem bloquear as entregas obrigatórias.
- Rafaela iniciará um protótipo no Figma para validar a estrutura visual antes da implementação.
- A implementação deverá consumir somente o contrato público do backend, sem acesso direto à futura API Python.

### Pendências identificadas em 16/07

- Consolidar o contrato de integração entre Backend e Data Science.
- Definir os campos obrigatórios da inferência do modelo.
- Definir como variáveis auxiliares serão substituídas, derivadas, estimadas ou removidas.
- Confirmar o modelo e os artefatos que serão carregados pela futura API Python.
- Implementar e documentar a API Python de inferência.
- Implementar o client HTTP e o fallback no backend.
- Implementar e validar tecnicamente a integração com OCI.
- Submeter o protótipo inicial do frontend para avaliação da equipe.
- Melhorar a participação e a divisão de tarefas na frente de Data Science.

### Próximos passos definidos em 16/07

- Solicitar posicionamento de Túlio e Miguel sobre o estado atual, bloqueios e divisão das tarefas de Data Science.
- Manter o backend preparado para adaptação após a definição do contrato de inferência.
- Avançar no protótipo visual do frontend sem comprometer as entregas obrigatórias do MVP.
- Acompanhar as issues relacionadas à API Python, integração HTTP, fallback e OCI.
- Atualizar a documentação somente após decisões confirmadas e evidências técnicas.
- Registrar no Discord os alinhamentos e compromissos assumidos pelas frentes.

### Observações sobre a reunião de 16/07

Esta ata registra somente os pontos operacionais relevantes da Sprint Demo. A transcrição completa permanece como fonte interna de apoio e não deve ser publicada no repositório por conter trechos informais e discussões que não fazem parte da documentação oficial do projeto.

---

## 20/07/2026 — Sprint Planning Semana 2

### Contexto da reunião de 20/07

Sprint Planning realizada para revisar o andamento do projeto EnergiAI, identificar os bloqueios restantes entre Backend e Data Science, alinhar a elaboração da nova base sintética, acompanhar o início do frontend e definir atividades que podem avançar paralelamente durante a Semana 2.

### Participantes da reunião de 20/07

- Fábio
- Alan
- Gustavo
- Lucas
- Rafaela
- Túlio

### Ausências registradas em 20/07

- Adriana não participou, mas justificou previamente que realizaria uma prova na faculdade.
- Miguel não participou e não apresentou justificativa.

### Avanços apresentados em 20/07

- O backend permanece avançado e preparado para adaptação após a definição do contrato de integração com Data Science.
- Rafaela informou que iniciou a estrutura de pastas e a organização inicial do projeto frontend.
- Fábio iniciou a elaboração de uma proposta de especificação para orientar a criação da base sintética.
- Túlio informou que está trabalhando em uma nova abordagem utilizando somente as variáveis mínimas indicadas no enunciado do desafio.
- Lucas informou que pretende iniciar testes relacionados à OCI durante a semana, independentemente das pendências atuais de Data Science.

### Alinhamentos sobre Data Science em 20/07

- A nova base sintética deverá priorizar as variáveis mínimas previstas no enunciado oficial do projeto.
- A proposta de especificação elaborada por Fábio deverá ser tratada como documento inicial de discussão, e não como contrato definitivo ou comprovação de viabilidade do modelo.
- Túlio e os integrantes do backend deverão revisar a proposta antes ou durante a geração da nova base, reduzindo o risco de retrabalho.
- A viabilidade matemática e estatística da base ainda precisa ser validada por meio da geração dos dados, treinamento do modelo e análise dos resultados.
- A base deverá permitir que o modelo identifique padrões sem depender exclusivamente de uma ou duas variáveis.
- O mesmo intervalo de consumo poderá conter exemplos de perfis eficientes, moderados e ineficientes, desde que os demais atributos justifiquem a classificação.
- Túlio informou que tentará concluir uma nova versão da base e do modelo após validar a abordagem técnica em estudo.
- Dúvidas, bloqueios, resultados parciais e alterações relevantes deverão ser compartilhados com a equipe durante o desenvolvimento.
- A documentação e os artefatos de Data Science deverão ser versionados no GitHub por meio de branch e Pull Request para permitir revisão colaborativa.

### Alinhamentos sobre Backend em 20/07

- O principal bloqueio do backend continua sendo a definição estável dos dados de entrada e da resposta esperada da futura API de Data Science.
- O backend poderá revisar a proposta de especificação da base para verificar compatibilidade com o contrato público atual.
- Atividades independentes da integração com Data Science poderão continuar durante a Semana 2.
- A implementação não deverá tratar propostas ou variáveis ainda em estudo como contrato definitivo.
- A documentação deverá continuar diferenciando o funcionamento local atual da arquitetura futura com modelo de Machine Learning.

### Estado de OCI em 20/07

- As atividades relacionadas à OCI podem avançar separadamente da definição da base sintética.
- Lucas informou que pretende iniciar testes durante a semana.
- A integração com OCI ainda não deve ser considerada concluída sem conexão, testes, persistência e evidência técnica verificável.

### Estado do frontend em 20/07

- Rafaela iniciou a estrutura inicial do projeto frontend.
- O frontend permanece como entrega complementar e não deve bloquear os requisitos obrigatórios do MVP.
- A interface deverá consumir o contrato público do backend após sua consolidação.

### Pendências identificadas em 20/07

- Concluir e disponibilizar a proposta de especificação da base sintética.
- Revisar a proposta entre Fábio, Túlio e integrantes do backend.
- Gerar uma nova versão da base sintética utilizando as variáveis mínimas do enunciado.
- Validar a distribuição dos dados e a viabilidade do treinamento supervisionado.
- Documentar as regras utilizadas na geração das variáveis e da classificação.
- Definir o contrato estável de inferência entre Data Science e Backend.
- Definir os artefatos que serão carregados pela futura API Python.
- Implementar e documentar a API Python de inferência.
- Implementar o cliente HTTP e o fallback no backend.
- Implementar, testar e registrar evidência técnica da integração com OCI.
- Continuar a evolução do frontend sem comprometer os requisitos obrigatórios.
- Confirmar o horário exato da próxima reunião obrigatória.

### Próximos passos definidos em 20/07

- Fábio deverá concluir e disponibilizar a ata e a proposta de especificação da base sintética.
- Túlio deverá revisar a proposta, continuar os testes de geração da base e compartilhar os resultados obtidos.
- Lucas e os integrantes do backend deverão revisar a compatibilidade da proposta com a API e continuar atividades independentes da integração.
- Lucas deverá iniciar os testes relacionados à OCI conforme disponibilidade durante a semana.
- Rafaela deverá continuar a estruturação do frontend e apresentar os avanços para validação da equipe.
- A equipe deverá utilizar Pull Requests para revisar, comentar e aprimorar os documentos e artefatos produzidos.
- A próxima reunião obrigatória foi prevista para quinta-feira, 23/07/2026, entre 14h e 16h, com horário exato ainda pendente de confirmação.

### Observações sobre a reunião de 20/07

Esta ata registra somente os pontos operacionais relevantes da Sprint Planning. A proposta de especificação da base sintética ainda deverá passar por revisão técnica e não representa um contrato definitivo. A transcrição completa permanece como fonte interna de apoio e não deve ser publicada no repositório por conter trechos informais e discussões que não fazem parte da documentação oficial do projeto.

---

## 23/07/2026 — Sprint Meet obrigatória

### Contexto da reunião de 23/07

Sprint Meet obrigatória realizada para compartilhar os avanços da Semana 2, acompanhar o ambiente técnico, revisar o progresso de Data Science e Frontend, identificar bloqueios e alinhar a continuidade das atividades pelos canais oficiais.

### Participantes confirmados em 23/07

- Fábio
- Gustavo
- Lucas
- Alan
- Rafaela
- Adriana, com participação pelo chat

### Ausências registradas em 23/07

- Túlio não participou e justificou previamente que estaria em outra reunião.
- Miguel não participou e não apresentou justificativa.

### Observação operacional sobre participação

- Miguel participou da primeira reunião da equipe, mas não teve participação confirmada nas reuniões posteriores analisadas.

### Avanços apresentados em 23/07

- Lucas informou que concluiu uma configuração técnica, abriu o Pull Request correspondente e disponibilizou o ambiente para consulta e testes.
- O acesso ao ambiente dependerá de credenciais compartilhadas individualmente, sem publicação de senhas em canais abertos ou no repositório.
- Fábio informou que a implementação oficial de Data Science está sendo realizada de forma incremental, com alterações pequenas, testes, documentação e commits rastreáveis.
- A construção de Data Science segue o enunciado oficial, o contrato atual do backend e a Especificação V2 aprovada.
- Foi considerada válida a disponibilização de uma primeira versão funcional de Data Science antes da conclusão integral, permitindo testes antecipados pelo backend.
- Rafaela informou avanço no formulário de análise energética e intenção de abrir o respectivo Pull Request.
- Rafaela também iniciou a construção da página principal do frontend.

### Alinhamentos sobre Backend e infraestrutura em 23/07

- O acesso ao ambiente deverá ser concedido individualmente, com privilégios mínimos necessários para consulta e validação.
- Quando algum segredo precisar ser transmitido, deverão ser utilizados somente canais privados e temporários, sem publicação no repositório ou em canais abertos.
- Nenhuma informação sensível deverá ser publicada no Discord, incluída em prints públicos ou versionada no GitHub.
- Alterações no ambiente deverão ser previamente alinhadas com o responsável técnico.
- A situação de OCI deverá continuar sendo comprovada por código, testes, conexão, persistência e demais evidências técnicas registradas no repositório.

### Alinhamentos sobre Data Science em 23/07

- A implementação continuará dividida em unidades pequenas e revisáveis.
- Cada etapa deverá possuir testes e documentação proporcionais ao seu escopo.
- O objetivo imediato é disponibilizar uma primeira versão utilizável para permitir testes de integração com o backend.
- Dataset, pipeline, modelo e artefatos deverão permanecer compatíveis com as cinco entradas do contrato vigente.
- Dúvidas, bloqueios e resultados parciais deverão ser comunicados durante a semana, sem aguardar exclusivamente as reuniões obrigatórias.
- A participação efetiva de outros integrantes da frente de Dados ainda precisa ser confirmada.
- A concentração das atividades de Data Science permanece como risco para o cronograma.

### Alinhamentos sobre Frontend em 23/07

- O formulário deverá coletar as cinco entradas previstas no contrato público do backend.
- Foi discutida a possibilidade de limitar a quantidade de equipamentos informada pelo usuário.
- Qualquer limite deverá ser definido em conjunto entre Frontend, Backend e Data Science.
- O valor não deverá ser arbitrário nem incompatível com o schema, o dataset ou as validações do backend.
- O frontend deverá continuar consumindo somente a API pública do backend.

### Governança e comunicação em 23/07

- As reuniões obrigatórias deverão priorizar status, decisões, bloqueios e próximos passos.
- Ajustes operacionais e pedidos de revisão deverão continuar durante a semana nos canais oficiais.
- Código, documentação, decisões e evidências relevantes deverão permanecer registrados no GitHub.
- Informações críticas não deverão existir somente em conversas com ferramentas de inteligência artificial.
- A equipe poderá apontar processos redundantes, riscos e oportunidades de simplificação de forma direta e colaborativa.
- Pull Requests deverão continuar sendo revisados antes do merge.

### Disponibilidade operacional de Fábio

- Fábio informou que poderá ter disponibilidade temporariamente reduzida nos dias seguintes.
- As atividades imediatas deverão permanecer documentadas, versionadas e compreensíveis para permitir a continuidade do trabalho pela equipe.
- Essa informação é registrada somente para planejamento operacional, sem detalhamento pessoal adicional.

### Pendências identificadas em 23/07

- Compartilhar o acesso ao ambiente técnico de forma segura.
- Disponibilizar uma primeira versão utilizável dos componentes de Data Science.
- Confirmar a participação e a divisão das tarefas na frente de Dados.
- Abrir e revisar o Pull Request do formulário do frontend.
- Continuar a implementação da página principal.
- Definir de forma conjunta os limites de entrada do formulário.
- Validar a compatibilidade entre dataset, FastAPI, backend e frontend.
- Preservar evidências técnicas relacionadas à OCI.
- Manter decisões e avanços atualizados no GitHub e nos canais técnicos correspondentes.

### Próximos passos definidos em 23/07

- Lucas deverá orientar o acesso seguro ao ambiente técnico e continuar as atividades de infraestrutura e backend.
- Fábio deverá continuar a implementação incremental de Data Science e disponibilizar uma versão testável assim que os componentes mínimos estiverem validados.
- Rafaela deverá abrir o Pull Request do formulário e continuar a página principal.
- Frontend, Backend e Data Science deverão alinhar qualquer novo limite aplicado às entradas.
- A equipe deverá comunicar dúvidas, bloqueios e revisões durante a semana, utilizando GitHub e Discord conforme o tipo de evidência.
- As tarefas prioritárias deverão permanecer documentadas para reduzir riscos decorrentes de indisponibilidade temporária de integrantes.

### Riscos registrados em 23/07

- Concentração das atividades de Data Science em poucos integrantes.
- Divergência de validações entre Frontend, Backend e dataset.
- Exposição indevida de credenciais ou informações sensíveis.
- Dependência excessiva de ferramentas externas para preservar contexto e decisões.
- Redução temporária da disponibilidade operacional de Fábio.
- Atraso da integração caso a primeira versão de Data Science não seja disponibilizada para testes.

### Observações sobre a reunião de 23/07

Esta ata registra somente os pontos operacionais relevantes da Sprint Meet obrigatória. A transcrição revisada permanece como fonte interna de apoio e não deve ser publicada no repositório. A identificação dos participantes foi limitada às pessoas confirmadas pelo contexto disponível, e nenhuma credencial, senha ou informação sensível foi incluída.

---

## 27/07/2026 — Sprint Meet obrigatória da Semana 3

### Contexto da reunião de 27/07

Sprint Meet obrigatória realizada em 27 de julho de 2026, às 18h, para revisar o início da Semana 3 do projeto EnergiAI, compartilhar o andamento das frentes, alinhar a continuidade de Data Science, acompanhar as entregas de Frontend e infraestrutura e reforçar a comunicação pelos canais oficiais.

A reunião também foi utilizada para atualizar Túlio sobre o caminho técnico adotado pela equipe durante sua indisponibilidade recente e alinhar como sua contribuição poderá ser integrada ao trabalho oficial em andamento.

### Participantes confirmados em 27/07

- Fábio
- Gustavo
- Lucas
- Rafaela
- Túlio
- Alan
- Adriana

### Ausência registrada em 27/07

- Miguel não participou da reunião e não apresentou justificativa identificada até o encerramento.

### Avanços apresentados em 27/07

- O Backend foi considerado uma das frentes mais avançadas do projeto.
- Não foi apresentado novo bloqueio técnico relacionado ao funcionamento atual do Backend.
- O ambiente técnico continua disponível para consulta e validação pelos integrantes autorizados.
- Lucas reforçou que os interessados em acessar o ambiente deverão solicitar as informações por mensagem privada.
- Rafaela informou que o Pull Request da página de resultado da análise energética estava disponível para revisão.
- A página de resultado foi desenvolvida para apresentar os dados retornados pela API após o envio do formulário.
- Rafaela informou que iniciou o desenvolvimento da tela de painel ou dashboard.
- Fábio informou que a implementação oficial de Data Science continua sendo construída de forma incremental, documentada e testada.
- O trabalho de Data Science está sendo registrado na branch oficial da issue correspondente.
- Conforme a atualização apresentada na reunião, a branch possuía 49 commits relacionados à construção incremental do Dataset V2.
- O dataset oficial ainda estava em desenvolvimento e não foi apresentado como concluído.
- As etapas implementadas estavam sendo publicadas progressivamente para permitir acompanhamento, validação e revisão pela equipe.

### Atualização de Túlio sobre Data Science

- Túlio informou que havia desenvolvido uma nova versão de dataset baseada nas variáveis previstas no enunciado do desafio.
- O artefato mencionado não foi apresentado nem tecnicamente avaliado durante a reunião.
- Túlio informou que não sabia se deveria disponibilizar sua versão ou seguir o caminho técnico já adotado pela equipe.
- Foi esclarecido que, durante sua indisponibilidade, a equipe continuou o desenvolvimento para evitar que a frente de Data Science permanecesse bloqueada.
- Fábio explicou que a especificação, as decisões e as etapas implementadas foram publicadas nos canais oficiais e no GitHub.
- Foi reforçado que a ausência de manifestação sobre a documentação tornou necessário continuar o trabalho sem aguardar indefinidamente por uma resposta.
- Túlio declarou que respeitou a votação anterior e que compreendeu o caminho adotado pela equipe.
- Foi esclarecido que a decisão de não adotar o notebook anterior como contrato definitivo não eliminou a necessidade de sua participação.
- O notebook e os estudos anteriores permanecem como referências históricas, mas não substituem a Especificação V2 e a implementação oficial em andamento.
- Túlio informou que estava configurando um computador novo e resolvendo seu acesso ao GitHub.
- Foi solicitado que ele revise a documentação, os canais do Discord e o estado atual da branch antes de iniciar uma implementação paralela.
- Após compreender o estado atual, Túlio poderá apresentar dúvidas, sugestões, revisões e propostas de contribuição.
- Túlio concordou em consultar os materiais disponíveis e se atualizar sobre a abordagem vigente.

### Alinhamentos sobre Data Science em 27/07

- A equipe decidiu continuar a implementação oficial já iniciada.
- Não deverá ser criado um segundo dataset paralelo sem revisão, alinhamento e integração com o trabalho atual.
- A Especificação V2 aprovada permanece como referência técnica para o dataset e para a futura modelagem.
- O trabalho deverá continuar compatível com as cinco entradas previstas no contrato público:
  - `consumo_kwh`;
  - `uso_horario_pico`;
  - `quantidade_equipamentos`;
  - `tipo_imovel`;
  - `horas_alto_consumo`.
- A implementação continuará sendo dividida em etapas pequenas, testáveis e rastreáveis.
- Novas contribuições deverão partir da leitura do código e da documentação já existentes.
- Sugestões técnicas são bem-vindas, desde que sejam discutidas com a equipe e compatibilizadas com Backend, Frontend e requisitos do MVP.
- Resultados parciais, dificuldades, bloqueios e indisponibilidades deverão ser comunicados durante a semana.
- A participação na frente não deve ocorrer somente durante as reuniões obrigatórias.
- A concentração das atividades de Data Science em poucos integrantes continua sendo um risco para o cronograma.
- A ausência de atualização de Miguel permanece como ponto de atenção para a divisão efetiva das atividades de Dados.
- A prioridade permanece na conclusão dos componentes obrigatórios antes da ampliação do escopo.

### Alinhamentos sobre Frontend em 27/07

- A página de resultado da análise energética foi apresentada como entrega disponível para revisão.
- A tela deverá exibir somente informações retornadas pela API pública do Backend.
- O fluxo deverá apresentar os dados previstos no contrato vigente, incluindo:
  - categoria;
  - score;
  - probabilidade;
  - custo estimado;
  - recomendações;
  - fonte da classificação, quando disponível no contrato público.
- O desenvolvimento do painel ou dashboard foi iniciado como etapa posterior.
- O dashboard não deverá comprometer a conclusão das telas necessárias para o fluxo principal do MVP.
- Alterações no Frontend deverão permanecer compatíveis com o contrato público do Backend.
- O Pull Request da página de resultado deverá passar pelo processo normal de revisão e checks antes do merge.

### Alinhamentos sobre Backend e infraestrutura em 27/07

- Não foram relatados novos bloqueios relevantes na frente de Backend.
- O acesso ao ambiente técnico permanece disponível mediante solicitação ao responsável.
- Credenciais e informações de acesso deverão ser compartilhadas exclusivamente por canal privado.
- Nenhuma senha, token, wallet ou informação sensível deverá ser publicada no Discord ou versionada no GitHub.
- Foi mencionada a possibilidade de avaliar futuramente a execução do Backend em uma instância de computação.
- Essa possibilidade foi tratada somente como ideia de evolução.
- Nenhuma nova implantação em instância de computação foi aprovada ou apresentada como concluída durante a reunião.
- A equipe deverá priorizar as entregas obrigatórias antes de assumir novo escopo de infraestrutura.
- Os integrantes interessados em conhecer ou validar o ambiente deverão solicitar o acesso diretamente a Lucas.

### Discussão sobre funcionalidades adicionais em 27/07

- Gustavo perguntou se a equipe pretendia adicionar novas funcionalidades durante a Semana 3.
- Foi alinhado que a frente de Data Science não deverá ampliar o escopo antes de concluir os componentes já planejados.
- Outras ideias poderão ser avaliadas após a estabilização das entregas obrigatórias.
- O projeto deverá evitar funcionalidades adicionais que aumentem o risco de atraso do MVP.
- Qualquer nova proposta deverá ser avaliada considerando:
  - obrigatoriedade para o desafio;
  - impacto no prazo;
  - dependências entre as frentes;
  - necessidade de alteração no contrato;
  - risco de retrabalho;
  - evidência técnica que será gerada.

### Governança e comunicação em 27/07

- O GitHub permanece como principal fonte de evidência técnica do projeto.
- O Discord deverá continuar sendo utilizado para atualizações, dúvidas, decisões e alinhamentos durante a semana.
- As reuniões obrigatórias não substituem o acompanhamento assíncrono das atividades.
- Cada integrante deverá comunicar, sempre que possível:
  - o que está desenvolvendo;
  - quais dificuldades encontrou;
  - quando estiver temporariamente indisponível;
  - qual contribuição pretende assumir;
  - quando precisar de revisão ou apoio.
- A equipe deverá evitar aguardar a próxima reunião para comunicar bloqueios que podem ser resolvidos pelos canais oficiais.
- Decisões que afetem várias frentes não deverão ser tomadas individualmente.
- Propostas técnicas deverão ser apresentadas para discussão e compatibilização com o trabalho já realizado.
- O acompanhamento do Discord poderá ser realizado mesmo quando não for possível trabalhar diretamente no código.
- Foi reforçado que participação efetiva envolve comunicação, revisão, colaboração e geração de evidências, não apenas presença nas reuniões.
- Os integrantes deverão consultar as atas, issues, Pull Requests, commits e mensagens dos canais correspondentes para acompanhar a evolução do projeto.

### Disponibilidade operacional

- Fábio informou que estava com disponibilidade parcialmente reduzida, mas que continuaria acompanhando e desenvolvendo as atividades prioritárias.
- A equipe reforçou que indisponibilidades pessoais ou profissionais são compreensíveis, desde que sejam comunicadas para permitir redistribuição e continuidade.
- As atividades críticas deverão permanecer documentadas e compreensíveis para reduzir dependência de uma única pessoa.

### Decisões registradas em 27/07

1. A implementação oficial do Dataset V2 continuará sendo a que já está registrada na branch da issue correspondente.
2. Não será iniciada uma segunda implementação paralela antes da revisão do estado atual.
3. Túlio deverá primeiro revisar a documentação, o código e as decisões já registradas.
4. Após a atualização, Túlio poderá propor uma contribuição compatível com a implementação vigente.
5. A página de resultado do Frontend deverá seguir para revisão pelo fluxo normal de Pull Request.
6. O painel ou dashboard poderá continuar em desenvolvimento sem comprometer as entregas prioritárias.
7. Novas funcionalidades permanecerão subordinadas à conclusão do MVP.
8. Credenciais do ambiente continuarão sendo compartilhadas somente por canal privado.
9. Dúvidas e bloqueios deverão ser comunicados durante a semana, sem aguardar exclusivamente as reuniões obrigatórias.
10. Decisões técnicas compartilhadas entre frentes deverão continuar sendo discutidas e registradas nos canais oficiais.

### Pendências identificadas em 27/07

- Túlio deverá concluir a configuração de seu computador e confirmar o acesso ao GitHub.
- Túlio deverá revisar a Especificação V2, a issue de Data Science, a branch oficial e as atualizações publicadas no Discord.
- Túlio deverá informar qual contribuição concreta poderá assumir após compreender o estado atual.
- A equipe deverá continuar a implementação e validação do Dataset V2.
- Uma primeira versão testável dos componentes oficiais de Data Science ainda deverá ser disponibilizada para integração.
- Os artefatos finais de dataset, modelagem e integração não foram apresentados como concluídos durante a reunião.
- A página de resultado do Frontend deverá passar por revisão antes do merge.
- O painel ou dashboard deverá continuar sem comprometer as funcionalidades prioritárias.
- O acesso ao ambiente técnico deverá continuar sendo fornecido de forma segura.
- A equipe deverá evitar a criação de implementações paralelas sem alinhamento prévio.
- Novas funcionalidades deverão permanecer subordinadas à conclusão dos requisitos obrigatórios do MVP.
- A participação e a divisão das atividades de Data Science ainda precisam ser fortalecidas.
- A situação de Miguel deverá permanecer registrada somente como ausência sem justificativa, até que exista manifestação ou confirmação formal.

### Próximos passos definidos em 27/07

- Fábio deverá continuar a implementação incremental da issue oficial de Data Science.
- Fábio deverá manter as etapas documentadas, testadas e registradas na branch correspondente.
- Túlio deverá se atualizar por meio da documentação, do GitHub e dos canais do Discord.
- Túlio deverá apresentar dúvidas, sugestões e uma proposta de contribuição compatível com a implementação atual.
- Lucas deverá continuar apoiando Backend, infraestrutura, governança e acesso seguro ao ambiente.
- Rafaela deverá acompanhar a revisão da página de resultado e continuar o desenvolvimento do painel.
- Os integrantes deverão revisar Pull Requests e registrar comentários técnicos quando forem solicitados.
- A equipe deverá manter a comunicação durante a semana, sem concentrar atualizações apenas nas reuniões obrigatórias.
- Funcionalidades adicionais deverão ser avaliadas somente após as entregas obrigatórias ou quando reduzirem um risco real do MVP.
- Os bloqueios e decisões relevantes deverão continuar sendo registrados no GitHub ou no canal técnico correspondente.

### Riscos registrados em 27/07

- Concentração da implementação de Data Science em poucos integrantes.
- Falta de acompanhamento contínuo de integrantes responsáveis por frentes críticas.
- Desenvolvimento de datasets ou soluções paralelas sem integração com a especificação vigente.
- Retrabalho causado por propostas desenvolvidas sem revisão do estado atual.
- Bloqueio de contribuição devido a problemas de acesso ao GitHub.
- Ampliação prematura do escopo antes da conclusão do MVP.
- Falta de revisão da página de resultado do Frontend.
- Dependência das reuniões obrigatórias para comunicar informações que deveriam circular durante a semana.
- Decisões individuais incompatíveis com contratos compartilhados entre Frontend, Backend e Data Science.
- Dependência excessiva de poucos integrantes para preservar o andamento e o contexto técnico do projeto.

### Observações sobre a reunião de 27/07

Esta ata registra somente os pontos operacionais e técnicos relevantes da Sprint Meet obrigatória da Semana 3.

A transcrição original possui falhas de reconhecimento, interrupções, conversas informais e trechos sem identificação segura dos participantes. Por esse motivo, foram preservados apenas os pontos cujo sentido pôde ser confirmado pelo contexto da reunião e pelas evidências disponíveis.

A transcrição completa deverá permanecer como fonte interna de apoio e não deverá ser publicada no repositório. Comentários pessoais, expressões informais, falhas de captação e trechos sem relevância para o projeto não foram incluídos nesta ata.

A ausência de Miguel foi registrada como fato. Não foi incluída qualquer afirmação sobre abandono do projeto porque não existe confirmação formal disponível.

Esta ata deverá passar por revisão via Pull Request antes de ser considerada definitiva.

---

## 30/07/2026 — Sprint Demo

### Contexto da Sprint Demo de 30/07

Sprint Demo realizada para revisar os avanços da semana nas frentes de Backend,
infraestrutura, Frontend e Data Science, além de discutir a possibilidade de
implementação das funcionalidades de controle de acesso sem comprometer o MVP.

Esta ata foi elaborada com base na gravação da reunião, na transcrição
estruturada disponibilizada posteriormente, no resumo publicado por Gustavo no
Discord e nos avisos de ausência registrados pela equipe.

A gravação e a transcrição integral permanecem como fontes internas de apoio. O
repositório contém somente o registro objetivo dos pontos relevantes para o
projeto.

### Participantes da Sprint Demo de 30/07

- Gustavo Kenzo
- Alan Ryan da Silva Domingues
- Rafaela Pereira Campos

### Ausências justificadas em 30/07

- Fábio Andrade informou antecipadamente que não poderia participar.
- Lucas Rossoni Dieder informou antecipadamente que não poderia participar.
- Adriana Firmino dos Santos informou que não participaria porque estava sem
  energia elétrica após uma ventania ocorrida no Rio de Janeiro.

### Ausências sem justificativa registrada em 30/07

- Miguel Luan Tavares Leite
- Túlio Braga

### Backend e infraestrutura

- Os participantes revisaram os avanços de Backend e infraestrutura registrados
  no GitHub e atribuídos ao trabalho técnico de Lucas.
- Foram mencionadas atividades de configuração e validação envolvendo teste em
  Python.
- Foi mencionado o provisionamento de uma instância Compute Free.
- Foram mencionados avanços relacionados ao CI e ao deploy.
- Também foi mencionada a configuração de probes de liveness e readiness.
- Lucas havia aberto issues para organizar os próximos passos da frente.
- Como Lucas não participou da reunião, esses pontos foram revisados pelos
  participantes com base no estado registrado no GitHub, e não apresentados
  diretamente por ele durante o encontro.

### Frontend

- A implementação do dashboard havia sido aprovada por Lucas.
- O histórico e o detalhamento das análises ainda estavam pendentes.
- Rafaela informou que já havia iniciado o desenvolvimento dessas duas
  funcionalidades.
- Rafaela também informou que sua disponibilidade diminuiria na semana seguinte
  devido ao retorno das aulas da faculdade.
- Essa redução de disponibilidade foi identificada como um possível bloqueio
  para novas telas relacionadas a cadastro e controle de acesso.
- Rafaela se comprometeu a revisar a documentação já produzida e registrar
  eventuais dúvidas no Discord.

### Data Science

- A equipe relatou que aguardava a entrega da frente de Data Science.
- Não houve decisão técnica de modelagem durante a reunião.
- Este registro representa exclusivamente o estado relatado em 30/07/2026 e não
  substitui atualizações posteriores registradas no GitHub ou no Discord.

### Controle de acesso

- A equipe discutiu a possibilidade de implementar cadastro e controle de
  acesso.
- A funcionalidade foi considerada útil, mas o prazo restante foi identificado
  como uma limitação relevante.
- Alan e Rafaela avaliaram que seria possível tentar a implementação, desde que
  ela não comprometesse o MVP.
- A equipe registrou uma intenção condicional de prosseguir com essa frente.
- O início, a organização e a distribuição das respectivas issues permaneceram
  dependentes de novo alinhamento com Lucas e com o restante da equipe.
- A discussão deveria continuar no Discord.
- Caso não houvesse tempo suficiente, o MVP deveria ser preservado sem essa
  ampliação de escopo.

### Cronograma e gestão

- O prazo restante do projeto foi identificado como um ponto de atenção.
- A equipe considerou que ainda possuía a semana da reunião e a semana seguinte
  para concluir as entregas técnicas.
- A última semana do cronograma da No Country estaria direcionada
  principalmente à preparação e ao envio das entregas.
- A disponibilidade reduzida do Frontend poderia afetar o desenvolvimento de
  funcionalidades adicionais.
- O GitHub permaneceu como fonte principal para acompanhamento das issues e dos
  avanços técnicos.

### Próximos passos definidos em 30/07

- Continuar o histórico e o detalhamento das análises no Frontend.
- Revisar a documentação já produzida e encaminhar dúvidas pelo Discord.
- Discutir com Lucas e com o restante da equipe a organização das issues de
  cadastro e controle de acesso.
- Avaliar o início dessas issues somente quando a execução não colocar o MVP em
  risco.
- Continuar acompanhando os avanços e bloqueios por meio do GitHub e do Discord.

### Observações sobre a Sprint Demo de 30/07

Esta ata registra somente os pontos técnicos, operacionais e de gestão
sustentados pelas fontes disponíveis.

A gravação contém falas informais, repetições e trechos próprios de uma conversa
de alinhamento. Por esse motivo, a transcrição literal não foi adicionada ao
repositório.

A menção ao trabalho técnico de Lucas não representa sua participação na
reunião. Sua ausência havia sido informada antecipadamente.

Os estados de tarefas e Pull Requests descritos nesta seção representam a
situação relatada em 30/07/2026 e não substituem atualizações posteriores do
GitHub.

Esta ata deverá passar por nova revisão via Pull Request antes de ser
considerada definitiva.

---

## 03/08/2026 — Sprint Planning

### Contexto da reunião de 03/08

Reunião de Sprint Planning realizada para alinhar o andamento das frentes de
Backend e Frontend, com base no planejamento já registrado no GitHub.

### Participantes da reunião de 03/08

- Alan
- Gustavo
- Lucas
- Rafaela

### Ausências registradas em 03/08

- Fábio informou antecipadamente que não poderia participar da reunião (ausência justificada).
- Miguel e Túlio estiveram ausentes, sem justificativa registrada até o momento.
- Adriana não participou da Sprint Planning.

### Comunicação anterior à reunião de 03/08

- Antes da realização da reunião, Adriana comunicou sua saída do projeto.
- Este registro é feito separadamente da lista de participantes e das decisões
  tomadas durante a reunião, por se tratar de uma comunicação ocorrida anteriormente ao encontro.

### Backend e Infraestrutura em 03/08

- O link público da API foi disponibilizado.
- A implementação da estrutura base do controle de acesso foi iniciada, revisada
  e está em etapa de ajustes.
- A liberação da estrutura de controle de acesso desbloqueará as próximas tarefas
  da frente, com expectativa de que o prazo de conclusão da função de autenticação
  autorização seja cumprido
- A issue de associar a análise energética ao usuário autenticado foi assumida,
  com conclusão prevista ao longo da semana.

### Frontend em 03/08

- A Pull Request feat(frontend): implementa histórico e detalhamento de análises
  já foi lançada e está aguardando merge.
- O deploy da aplicação na plataforma Vercel está planejado para o dia seguinte
  (04/08).
- Após a conclusão do deploy, será feita a liberação do CORS no backend para
  garantir a comunicação entre os ambientes.

### Data-Science

- O Data-Science foi citado como pendência, mas em fase de finalização, sendo
  apontado como o principal pendência do projeto no momento.

### Reviews e entregáveis em 03/08

- Foram revisadas a PR do Frontend e a PR referente à estrutura base do controle
  de acesso do Backend.
- Os entregáveis do site da NoCountry foram revisados e atualizados no próprio
  dia da reunião.
- Ficou definido que a PR da frente de Data-Science também seria analisada.

### Decisões registradas em 03/08

- O alinhamento das frentes segue em conformidade com o planejamento prévio
  registrado no GitHub.
- A gestão dos entregáveis do projeto e os processos de review de código seguem
  ativos e acompanhados.
- Novas tarefas serão distribuídas e puxadas ao longo da semana conforme o
  andamento das frentes.
- Foi comentado que a semana seguinte seria mais destinada às entregas do hackathon.

### Pendências identificadas em 03/08

- Concluir os ajustes na estrutura base do controle de acesso e liberar as tarefas
  pendentes.
- Concluir a associação da análise energética ao usuário autenticado.
- Realizar o merge da PR de histórico e detalhamento de análises do Frontend.
- Executar o deploy da aplicação no Vercel.
- Liberar o CORS no backend após o deploy do Frontend.
- Revisar a PR da frente de Data-Science.
- Preparar as entregas do hackathon previstas para a semana seguinte.

### Próximos passos definidos em 03/08

- Finalizar a estrutura base do controle de acesso e liberar as tarefas dependentes.
- Dar continuidade à associação da análise energética ao usuário
  autenticado.
- Concluir o deploy da aplicação no Vercel.
- Liberar o CORS no backend após o deploy do Frontend.
- Assumir novas tarefas ao longo da semana, incluindo a issue de refresh token e
  outras issues relacionadas a usuários autenticados.
- Revisar a PR do Data-Science.

### Observações sobre a reunião de 03/08

Esta ata foi revisada com base nas informações operacionais e técnicas fornecidas
na reunião, incluindo atualizações de entregáveis, reviews de PR, distribuição
de issues ao longo da semana e o planejamento das próximas entregas.

Os registros foram consolidados sem detalhar individualmente a atribuição de cada
ação, exceto quando a distinção foi relevante para o entendimento do andamento das
frentes.

Esta ata deverá passar por revisão via Pull Request antes de ser considerada
definitiva.
