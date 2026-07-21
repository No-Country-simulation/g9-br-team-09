# Frontend do EnergiAI

Frontend da aplicação EnergiAI, construído como SPA com React, TypeScript, Vite e Tailwind CSS.

## Stack

- React
- TypeScript
- Vite
- Tailwind CSS
- React Router Dom
- Axios

## Pré-requisitos

- Node.js 18+ (recomendado)
- npm

## Instalação

1. Abra o terminal em `frontend/`
2. Execute:

```bash
npm install
```

## Variáveis de ambiente

Copie o arquivo de exemplo e configure a URL base da API:

```bash
cp .env.example .env
```

Em seguida, ajuste `VITE_API_BASE_URL` no `.env` para seu backend Spring Boot.

> O frontend lê a URL da API em `src/shared/api/httpClient.ts` via `import.meta.env.VITE_API_BASE_URL`.

## Scripts

- `npm run dev` — inicia o servidor de desenvolvimento
- `npm run build` — gera a versão de produção
- `npm run preview` — pré-visualiza o build de produção localmente
- `npm run lint` — executa o ESLint
- `npm run lint:fix` — tenta corrigir problemas de lint automaticamente
- `npm run format` — formata o código com Prettier

## Estrutura do projeto

- `src/app/` — configuração global da aplicação e roteamento
- `src/features/` — funcionalidades específicas da aplicação
- `src/shared/` — componentes, hooks, contexto, API e estilos compartilhados

