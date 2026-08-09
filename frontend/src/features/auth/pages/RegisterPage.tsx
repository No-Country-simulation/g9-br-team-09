import { AuthLayout } from '../components/AuthLayout'
import { RegisterForm } from '../components/RegisterForm'

export function RegisterPage() {
  return (
    <AuthLayout
      asideTitle="Descubra a eficiência energética do seu imóvel"
      asideDescription="O EnergiAI analisa o perfil energético do seu imóvel e traz recomendações práticas para reduzir custos."
    >
      <h2 className="text-foreground mb-8 text-3xl font-semibold">
        Criar conta
      </h2>
      <RegisterForm />
    </AuthLayout>
  )
}
