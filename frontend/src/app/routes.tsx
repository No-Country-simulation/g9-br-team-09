import { createBrowserRouter, RouterProvider } from 'react-router-dom'

import { MainLayout } from '@/app/layout/MainLayout'
import { AnalysisDetailsPage } from '@/features/analysis-details/pages/DetailsPage'
import { AnalysisHistoryPage } from '@/features/analysis-history/pages/HistoryPage'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { AnalysisFormPage } from '@/features/energy-analysis/pages/EnergyAnalysisFormPage'
import { EnergyAnalysisHomePage } from '@/features/energy-analysis/pages/EnergyAnalysisHomePage'
import { AnalysisResultsPage } from '@/features/energy-analysis/pages/EnergyAnalysisResultsPage'
import { SummaryDashboardPage } from '@/features/summary-dashboard/pages/DashboardPage'

import { NotFoundPage } from './pages/NotFoundPage'
import { ProtectedRoute, PublicOnlyRoute } from './routes/AuthRoute'

const router = createBrowserRouter([
  {
    path: '/',
    element: <MainLayout />,
    children: [
      {
        index: true,
        element: <EnergyAnalysisHomePage />,
      },
    ],
  },
  {
    element: <PublicOnlyRoute />,
    children: [
      {
        path: '/login',
        element: <LoginPage />,
      },
      {
        path: '/cadastro',
        element: <RegisterPage />,
      },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <MainLayout />,
        children: [
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
            element: <AnalysisDetailsPage />,
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
])

export function AppRoutes() {
  return <RouterProvider router={router} />
}
