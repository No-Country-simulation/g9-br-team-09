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

- Node.js 20.19+ ou 22.12+
- npm, pnpm ou yarn

> O projeto não exige um gerenciador de pacotes específico. Utilize npm, pnpm ou yarn conforme sua preferência.

## Instalação

Abra o terminal em `frontend/` e instale as dependências com o gerenciador de sua preferência:

```bash
# npm
npm install

# pnpm
pnpm install

# yarn
yarn install
```

## Variáveis de ambiente

Copie o arquivo de exemplo e configure a URL base da API:

```bash
cp .env.example .env
```

Em seguida, ajuste `VITE_API_BASE_URL` no `.env` para seu backend Spring Boot.

> O frontend lê a URL da API em `src/shared/api/httpClient.ts` via `import.meta.env.VITE_API_BASE_URL`.

## Desenvolvimento

```bash
# npm
npm run dev

# pnpm
pnpm dev

# yarn
yarn dev
```

## Build

```bash
# npm
npm run build

# pnpm
pnpm build

# yarn
yarn build
```

## Lint

```bash
# npm
npm run lint

# pnpm
pnpm lint

# yarn
yarn lint
```

O script `lint:fix` tenta corrigir problemas de lint automaticamente.

## Formatação

```bash
# npm
npm run format

# pnpm
pnpm format

# yarn
yarn format
```

## Preview

```bash
# npm
npm run preview

# pnpm
pnpm preview

# yarn
yarn preview
```

## Estrutura do projeto

- `src/app/` — configuração global da aplicação e roteamento
- `src/features/` — funcionalidades específicas da aplicação
- `src/shared/` — componentes, hooks, contexto, API e estilos compartilhados
