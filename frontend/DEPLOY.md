# Publicação do frontend

## Plataforma utilizada

Vercel pela facilidade de integração com o Vite.

## URL pública

https://energiai.vercel.app

## Pré requisitos

Node.js 20.19+ ou 22.12+ e npm. O projeto usa `package-lock.json`, então a instalação deve ser feita com `npm ci`.

## Variáveis de ambiente

| Nome                | Descrição                                      | Ambiente               |
| ------------------- | ---------------------------------------------- | ---------------------- |
| `VITE_API_BASE_URL` | URL pública do backend consumido pelo frontend | Production and Preview |

O arquivo `.env.example` permanece versionado apenas como referência. As demais variantes `.env*` são ignoradas pelo Git e pelo deploy da Vercel.

Variáveis com prefixo `VITE_` são incorporadas ao bundle e ficam disponíveis no cliente. Portanto, `VITE_API_BASE_URL` deve conter somente a URL pública da API; senhas, tokens, credenciais e outras informações confidenciais não devem usar esse prefixo nem ser armazenadas em arquivos de ambiente do frontend.

## Comandos de build

Executados a partir da pasta `frontend`:

```bash
npm ci
npm run lint
npm run build
```

O script `build` executa `tsc -b && vite build`, com saída final em `dist/`.

## Procedimento de publicação

```bash
cd frontend
npm ci
npm run lint
npm run build

# a CLI do Vercel já está declarada em devDependencies e é instalada pelo lockfile
# criação do projeto
npx vercel
# adiciona variável de ambiente para production e preview
npx vercel env add VITE_API_BASE_URL production
npx vercel env add VITE_API_BASE_URL preview
# publicação em produção
npx vercel --prod
```

## Procedimento de atualização

1. Realizar as alterações necessárias e commitar
2. Rodar novamente lint e build localmente para validar antes de publicar:

```bash
   npm run lint
   npm run build
```

3. Garantir que `VITE_API_BASE_URL` continue configurada para `production` e `preview` no projeto da Vercel.
4. Publicar nova versão de produção:

```bash
   npx vercel --prod
```

## Configuração de rotas da SPA

Como o roteamento é feito através do React Router, foi criado `vercel.json` na raiz do frontend para redirecionar todas as rotas para `index.html`, evitando 404 em refresh ou em acesso direto a rotas internas:

```json
{
  "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }]
}
```

## Configuração necessária de CORS

**Status: concluída implementação no backend.**

| Item               | Valor                                                                                                                                                |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Origens a liberar  | `https://energiai.vercel.app` e os ambientes locais usados no desenvolvimento (`http://localhost:3000`, `http://localhost:5173`)                     |
| Métodos            | GET, POST, PUT, DELETE, OPTIONS                                                                                                                      |
| Endpoints afetados | Rotas públicas sob `/api/v1/**`, incluindo autenticação e análises; o contrato normativo permanece em [`docs/api-contract.md`](../docs/api-contract.md). |

## Diagnóstico de falhas

Registro de falhas encontradas durante o processo de publicação — todas já resolvidas. Serve como referência caso sintomas semelhantes voltem a ocorrer.

| Sintoma                                                    | Causa                                                               | Resolução                                                                                                                                   |
| ---------------------------------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Erro de CORS no console ao chamar a API                    | Origem `https://energiai.vercel.app` não estava liberada no backend | CORS liberado na OCI                                                                                                                        |
| `403` nas chamadas à API                                   | Rotas de análise energética não eram autorizadas                    | Resolvido junto com a liberação do CORS — chamadas retornando `200` normalmente                                                             |
| 404 ao acessar uma rota interna diretamente ou dar refresh | `vercel.json` não incluído no deploy, ou rewrite mal configurado    | Confirmar que `vercel.json` está commitado e presente na raiz do diretório publicado (`frontend`) e que o rewrite aponta para `/index.html` |

## Evidências da validação

Fluxo completo testado em produção (`https://energiai.vercel.app`), com chamadas à API retornando `200`:

- Listagem paginada de análises (`GET /api/v1/analise-energetica`);
- Painel exibindo dados reais (`GET /api/v1/analise-energetica/resumo`);
- Consulta de análise específica por id (`GET /api/v1/analise-energetica/{id}`);
- Criação de análise energética via formulário (`POST /api/v1/analise-energetica`).

A validação E2E posterior percorreu o happy path publicado com cadastro e
autenticação pela interface, análise, resultado, histórico, detalhe e dashboard.
Ela também comprovou o fluxo `ML_MODEL`, o fallback controlado
`RULE_BASED_FALLBACK` e o retorno a `ML_MODEL` após a restauração da
configuração. O procedimento, os resultados e as limitações estão em
[e2e/README.md](e2e/README.md); esta seção não duplica essa evidência.

## Limitações conhecidas

A publicação é controlada pela conta proprietária do projeto Vercel; confirme
com o responsável autorizado antes de executar `vercel --prod`. A validação
E2E cria usuários e análises descartáveis que permanecem persistidos porque a
interface não oferece exclusão pública.
