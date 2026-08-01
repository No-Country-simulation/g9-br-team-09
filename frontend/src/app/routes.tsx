import { createBrowserRouter, RouterProvider } from 'react-router-dom'

import { MainLayout } from '@/app/layout/MainLayout'
import { AnalysisFormPage } from '@/features/energy-analysis/pages/EnergyAnalysisFormPage'
import { EnergyAnalysisHomePage } from '@/features/energy-analysis/pages/EnergyAnalysisHomePage'
import { AnalysisResultsPage } from '@/features/energy-analysis/pages/EnergyAnalysisResultsPage'
import { AnalysisHistoryPage } from '@/features/history-analysis/pages/HistoryPage'
import { SummaryDashboardPage } from '@/features/summary-dashboard/pages/DashboardPage'

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
        element: <AnalysisFormPage />,
      },
      {
        path: 'resultado',
        element: <AnalysisResultsPage />,
      },
      {
        path: 'painel',
        element: <SummaryDashboardPage />,
      },
      {
        path: 'historico',
        element: <AnalysisHistoryPage />,
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
