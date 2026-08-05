# Publicação do frontend

## Plataforma utilizada
Vercel pela facilidade de integração com o Vite.

## URL pública
https://energiai.vercel.app

## Pré requisitos
Node.js na versão exigida pelo projeto ([ver README](/frontend/README.md)).

## Variáveis de ambiente
| Nome | Descrição | Ambiente |
|---|---|---|
| `VITE_API_BASE_URL` | URL pública do backend consumido pelo frontend | Production and Preview |

## Comandos de build
Executados a partir da pasta `frontend`:
```bash
npm ci
npm run lint
npm run build
```
Build gerado por `vite build`, saída em `dist/`.

## Procedimento de publicação
```bash
cd frontend
npm ci
npm run lint
npm run build
 
# instalação da CLI como devDependency, para reprodutibilidade via lockfile
npm install --save-dev vercel
# criação do projeto 
npx vercel             
# adiciona variável de ambiente                
npx vercel env add VITE_API_BASE_URL production 
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
3. Publicar nova versão de produção:
```bash
   npx vercel --prod
```

## Configuração de rotas da SPA
Como o roteamento é feito através do React Router, foi criado `vercel.json` na raiz do frontend para redirecionar todas as rotas para `index.html`, evitando 404 em refresh ou em acesso direto a rotas internas:
```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

## Configuração necessária de CORS
**Status: concluída implementação no backend.**
 
| Item | Valor |
|---|---|
| Origem a liberar | `https://energiai.vercel.app` |
| Métodos | GET, POST |
| Endpoints afetados |`POST /api/v1/analise-energetica`, `GET /api/v1/analise-energetica`, `GET /api/v1/analise-energetica/resumo` e `GET /api/v1/analise-energetica/{id}` |

## Diagnóstico de falhas
Registro de falhas encontradas durante o processo de publicação — todas já resolvidas. Serve como referência caso sintomas semelhantes voltem a ocorrer.
 
| Sintoma | Causa | Resolução |
|---|---|---|
| Erro de CORS no console ao chamar a API | Origem `https://energiai.vercel.app` não estava liberada no backend | CORS liberado na OCI |
| `403` nas chamadas à API | Rotas de análise energética não eram autorizadas | Resolvido junto com a liberação do CORS — chamadas retornando `200` normalmente |
| 404 ao acessar uma rota interna diretamente ou dar refresh | `vercel.json` não incluído no deploy, ou rewrite mal configurado | Confirmar que `vercel.json` está commitado e presente na raiz do diretório publicado (`frontend`); verificar na aba **Source** do deployment na Vercel |

## Evidências da validação
Fluxo completo testado em produção (`https://energiai.vercel.app`), com chamadas à API retornando `200`:
- Listagem paginada de análises (`GET /api/v1/analise-energetica`);
- Painel exibindo dados reais (`GET /api/v1/analise-energetica/resumo`);
- Consulta de análise específica por id (`GET /api/v1/analise-energetica/{id}`);
- Criação de análise energética via formulário (`POST /api/v1/analise-energetica`).

## Limitações conhecidas
Testes automatizados ainda não implementados (validação realizada manualmente).