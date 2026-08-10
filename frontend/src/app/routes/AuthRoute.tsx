import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '@/app/providers/auth/useAuth'

function SessionLoading() {
  return (
    <main
      className="flex min-h-screen items-center justify-center"
      role="status"
    >
      Carregando sessão...
    </main>
  )
}

export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <SessionLoading />
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

export function PublicOnlyRoute() {
  const { status } = useAuth()

  if (status === 'loading') {
    return <SessionLoading />
  }

  if (status === 'authenticated') {
    return <Navigate to="/analise-energetica" replace />
  }

  return <Outlet />
}
