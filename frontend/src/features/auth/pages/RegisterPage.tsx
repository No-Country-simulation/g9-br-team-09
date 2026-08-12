import { AuthLayout } from '../components/AuthLayout'
import { RegisterForm } from '../components/RegisterForm'

export function RegisterPage() {
  return (
    <AuthLayout>
      <h2 className="text-foreground mb-8 text-3xl font-semibold">
        Criar conta
      </h2>
      <RegisterForm />
    </AuthLayout>
  )
}
