import { createBrowserRouter, RouterProvider } from 'react-router-dom'

import { MainLayout } from '@/app/layout/MainLayout'
import { EnergyAnalysisHomePage } from '@/features/energy-analysis/pages/EnergyAnalysisHomePage'

import { NotFoundPage } from './pages/NotFoundPage'

const router = createBrowserRouter([
  {
    path: '/',
    element: <MainLayout />,
    children: [
      {
        index: true,
        element: <EnergyAnalysisHomePage />,
      },
      {
        path: 'analise-energetica',
        element: <h1>Análise energética</h1>,
      },
      {
        path: 'resultado',
        element: <h1>Resultado</h1>,
      },
      {
        path: 'painel',
        element: <h1>Painel</h1>,
      },
      {
        path: 'historico',
        element: <h1>Histórico</h1>,
      },
      {
        path: 'detalhes/:id',
        element: <h1>Detalhes</h1>,
      },
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
])

export function AppRoutes() {
  return <RouterProvider router={router} />
}
