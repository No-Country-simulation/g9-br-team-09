import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '@/app/providers/auth/useAuth'
import { getPostLoginPath } from '@/features/auth/navigation/post-login-path'

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
  const location = useLocation()

  if (status === 'loading') {
    return <SessionLoading />
  }

  if (status === 'authenticated') {
    return <Navigate to={getPostLoginPath(location.state)} replace />
  }

  return <Outlet />
}
