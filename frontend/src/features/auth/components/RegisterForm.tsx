import { Link, useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '@/app/providers/auth/useAuth'
import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'

import { getRegisterFormErrorMessage } from '../api/auth-api'
import { useAuthForm } from '../hooks/useAuthForm'
import { getPostLoginPath } from '../navigation/post-login-path'
import { registerSchema } from '../schemas/register'
import { FormField } from './FormField'
import { PasswordInput } from './PasswordInput'

export function RegisterForm() {
  const navigate = useNavigate()
  const location = useLocation()
  const { register } = useAuth()
  const {
    errors,
    formError,
    handleChange,
    handleSubmit,
    isSubmitting,
    setFormError,
    values,
  } = useAuthForm({
    initialValues: {
      fullName: '',
      email: '',
      password: '',
      confirmPassword: '',
    },
    schema: registerSchema,
    onValidSubmit: async (values) => {
      try {
        await register(values)
        void navigate(getPostLoginPath(location.state), { replace: true })
      } catch (error) {
        setFormError(getRegisterFormErrorMessage(error))
      }
    },
  })

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {formError && (
        <p
          role="alert"
          className="border-inefficient-badge-border bg-inefficient-badge-bg text-inefficient-badge-text rounded-xl border px-4 py-3 text-sm"
        >
          {formError}
        </p>
      )}

      <FormField
        label="Nome completo"
        htmlFor="fullName"
        error={errors.fullName}
      >
        <Input
          id="fullName"
          name="fullName"
          type="text"
          autoComplete="name"
          placeholder="ex: Maria da Silva"
          value={values.fullName}
          onChange={handleChange('fullName')}
          aria-invalid={Boolean(errors.fullName)}
          aria-describedby={errors.fullName ? 'fullName-error' : undefined}
        />
      </FormField>

      <FormField label="Email" htmlFor="email" error={errors.email}>
        <Input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="seu.email@exemplo.com"
          value={values.email}
          onChange={handleChange('email')}
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? 'email-error' : undefined}
        />
      </FormField>

      <FormField label="Senha" htmlFor="password" error={errors.password}>
        <PasswordInput
          id="password"
          name="password"
          autoComplete="new-password"
          placeholder="Mínimo de 8 caracteres"
          value={values.password}
          onChange={handleChange('password')}
          aria-invalid={Boolean(errors.password)}
          aria-describedby={errors.password ? 'password-error' : undefined}
        />
      </FormField>

      <FormField
        label="Confirme a senha"
        htmlFor="confirmPassword"
        error={errors.confirmPassword}
      >
        <PasswordInput
          id="confirmPassword"
          name="confirmPassword"
          autoComplete="new-password"
          placeholder="Digite a senha novamente"
          value={values.confirmPassword}
          onChange={handleChange('confirmPassword')}
          aria-invalid={Boolean(errors.confirmPassword)}
          aria-describedby={
            errors.confirmPassword ? 'confirmPassword-error' : undefined
          }
        />
      </FormField>

      <Button
        type="submit"
        variant="primary"
        isLoading={isSubmitting}
        loadingLabel="Criando conta..."
        className="mt-2 w-full"
      >
        {isSubmitting ? 'Criando conta...' : 'Criar conta'}
      </Button>

      <p className="text-muted-foreground text-center text-sm">
        Já possui uma conta?{' '}
        <Link
          to="/login"
          state={location.state}
          className="text-primary font-medium"
        >
          Entrar
        </Link>
      </p>
    </form>
  )
}
