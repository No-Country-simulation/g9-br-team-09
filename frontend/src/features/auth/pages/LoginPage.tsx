import { AuthLayout } from '../components/AuthLayout'
import { LoginForm } from '../components/LoginForm'

export function LoginPage() {
  return (
    <AuthLayout>
      <h2 className="text-foreground mb-8 text-3xl font-semibold">Entrar</h2>
      <LoginForm />
    </AuthLayout>
  )
}
