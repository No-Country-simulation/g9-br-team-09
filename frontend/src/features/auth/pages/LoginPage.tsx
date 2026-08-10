import { useLocation } from 'react-router-dom'

import { AuthLayout } from '../components/AuthLayout'
import { LoginForm } from '../components/LoginForm'

export function LoginPage() {
  const location = useLocation()
  const cameFromAnalysis = (
    location.state as { from?: { pathname?: unknown } } | null
  )?.from?.pathname === '/analise-energetica'

  return (
    <AuthLayout>
      <h2
        className={`text-foreground text-3xl font-semibold ${
          cameFromAnalysis ? 'mb-3' : 'mb-8'
        }`}
      >
        Entrar
      </h2>
      {cameFromAnalysis && (
        <p className="text-muted-foreground mb-8 text-sm">
          Faça login para continuar sua análise energética.
        </p>
      )}
      <LoginForm />
    </AuthLayout>
  )
}
