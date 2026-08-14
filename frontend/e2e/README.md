# Testes E2E do fluxo público

Esta suíte usa Playwright para percorrer o fluxo real pelo frontend público:

```text
navegador -> frontend -> backend -> FastAPI/ML -> Oracle -> frontend
```

Os testes criam uma conta descartável e única pela tela de cadastro, são
autenticados automaticamente e navegam para `/analise-energetica`. Interagem somente
com a interface e com requisições iniciadas naturalmente pelo navegador. A suíte não
chama a FastAPI ou o Oracle diretamente, não executa SQL e não usa classes internas
da aplicação.

## Pré-requisitos

- Node.js na versão definida em `package.json`;
- frontend, backend, FastAPI e Oracle implantados e integrados no ambiente alvo;
- URL pública do frontend acessível pela máquina de execução;
- permissão do responsável pelo ambiente para criar usuários e análises
  descartáveis.

Instale as dependências e o Chromium do Playwright:

```bash
cd frontend
npm ci
npx playwright install --with-deps chromium
```

## Configuração

| Variável              | Obrigatória | Padrão     | Descrição                                                              |
| --------------------- | ----------- | ---------- | ---------------------------------------------------------------------- |
| `E2E_BASE_URL`        | sim         | nenhum     | URL pública do frontend, sem credenciais, query string ou fragmento.   |
| `E2E_EXPECTED_SOURCE` | não         | `ML_MODEL` | Fonte esperada: `ML_MODEL` ou `RULE_BASED_FALLBACK`.                   |
| `E2E_REQUEST_TIMEOUT` | não         | `30000`    | Timeout positivo, em milissegundos, para ações, navegação e asserções. |

Não grave valores do ambiente em `.env`, no código ou nos artefatos. A URL deve
ser informada no processo que executa o Playwright:

```bash
E2E_BASE_URL=https://<frontend-publicado> \
E2E_EXPECTED_SOURCE=ML_MODEL \
E2E_REQUEST_TIMEOUT=30000 \
npm run test:e2e
```

O comando também pode apontar para um frontend local, desde que ele use os serviços
reais do ambiente que se pretende validar. Identifique esse ambiente explicitamente
no registro da execução; um frontend mockado não comprova este fluxo E2E.

## Cenários cobertos

- carregamento da página pública e disponibilidade do início da análise;
- criação de usuário único pela interface e autenticação automática, sem
  credenciais fixas;
- acessibilidade dos campos e bloqueio de um valor inválido antes do POST;
- análise com `420 kWh`, casa, uso em pico, dez equipamentos e oito horas;
- categoria pertencente ao contrato, score e probabilidade dentro dos intervalos,
  custo de `R$ 315,00`, recomendações preenchidas e fonte configurada;
- persistência observada pelo histórico e pela rota de detalhes, incluindo os dados
  enviados e calculados;
- atualização da rota de detalhes e retorno ao histórico;
- incremento relativo do total do painel, sem depender de dados globais prévios;
- segunda análise, atualização coerente da rota transitória de resultado, acesso
  direto sem estado com resposta vazia esperada e retorno ao formulário;
- fallback explícito, executado somente quando a fonte esperada for
  `RULE_BASED_FALLBACK`.

Categoria, score e probabilidade não são fixados: a suíte verifica os domínios e
intervalos apresentados pela interface, pois os valores concretos pertencem ao
modelo implantado.

## Fallback controlado

O teste nunca altera a implantação. Um operador autorizado deve abrir uma janela
controlada, seguir o procedimento de fallback de
`../../infra/deploy/oci/docs/operations.md`, configurar temporariamente
`ML_API_BASE_URL=http://127.0.0.1:9` e confirmar que somente o ambiente planejado
foi afetado. Então execute:

```bash
E2E_BASE_URL=https://<frontend-publicado> \
E2E_EXPECTED_SOURCE=RULE_BASED_FALLBACK \
npm run test:e2e
```

Restaure a configuração original pelo mesmo procedimento operacional assim que a
execução terminar, mesmo se o teste falhar, e confirme o retorno de `ML_MODEL` em
uma nova execução. Não use a suíte para reiniciar serviços, modificar variáveis ou
simular a FastAPI pelo navegador.

## Evidências e diagnóstico

O relatório HTML é salvo em `playwright-report/`. Em falha, o Playwright retém
também screenshot, vídeo e um anexo de erros do console/página em `test-results/`.
Abra o relatório com:

```bash
npm run test:e2e:report
```

O relatório registra data, hostname do ambiente, URL pública, commit e fonte
esperada. O cenário principal anexa a fonte observada na interface. O trace de rede
fica desativado intencionalmente para não serializar cookies ou headers de
autorização. Não publique artefatos sem revisar imagens e vídeos.

Ao diagnosticar uma falha, confirme nesta ordem: URL e commit, disponibilidade do
frontend, cadastro/autenticação automática, fonte esperada, integração interna do
backend e saúde do Oracle. Uma falha funcional deve ser registrada em issue própria
com passos e evidências sanitizadas; não ajuste regras de negócio para satisfazer o
teste.

## Limites e relação com outras suítes

O E2E comprova o comportamento percebido no navegador e a persistência por telas
públicas autenticadas. Ele não substitui testes unitários do frontend, testes de
integração Spring Boot/FastAPI, verificações diretas do Oracle ou smoke tests de
deploy. A criação de contas e análises é intencionalmente persistente; a aplicação
não oferece exclusão pela interface e a suíte não contorna essa limitação por API
ou SQL.
