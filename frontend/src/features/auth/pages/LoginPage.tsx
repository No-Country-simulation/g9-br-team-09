import { AuthLayout } from '../components/AuthLayout'
import { LoginForm } from '../components/LoginForm'

export function LoginPage() {
  return (
    <AuthLayout
      asideTitle="Descubra a eficiência energética do seu imóvel"
      asideDescription="O EnergiAI analisa o perfil energético do seu imóvel e traz recomendações práticas para reduzir custos."
    >
      <h2 className="text-foreground mb-8 text-3xl font-semibold">Entrar</h2>
      <LoginForm />
    </AuthLayout>
  )
}
