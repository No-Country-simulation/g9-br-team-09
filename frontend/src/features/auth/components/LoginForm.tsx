import { Link, useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '@/app/providers/auth/useAuth'
import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'

import { getLoginFormErrorMessage } from '../api/auth-api'
import { useAuthForm } from '../hooks/useAuthForm'
import { getPostLoginPath } from '../navigation/post-login-path'
import { loginSchema } from '../schemas/login'
import { FormField } from './FormField'
import { PasswordInput } from './PasswordInput'

export function LoginForm() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
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
      email: '',
      password: '',
    },
    schema: loginSchema,
    onValidSubmit: async (values) => {
      try {
        await login(values)
        void navigate(getPostLoginPath(location.state), { replace: true })
      } catch (error) {
        setFormError(getLoginFormErrorMessage(error))
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
          autoComplete="current-password"
          placeholder="Mínimo de 8 caracteres"
          value={values.password}
          onChange={handleChange('password')}
          aria-invalid={Boolean(errors.password)}
          aria-describedby={errors.password ? 'password-error' : undefined}
        />
      </FormField>

      <Button
        type="submit"
        variant="primary"
        isLoading={isSubmitting}
        loadingLabel="Entrando..."
        className="mt-2 w-full"
      >
        {isSubmitting ? 'Entrando...' : 'Entrar'}
      </Button>

      <p className="text-muted-foreground text-center text-sm">
        Não possui uma conta?{' '}
        <Link
          to="/cadastro"
          state={location.state}
          className="text-primary font-medium"
        >
          Cadastre-se
        </Link>
      </p>
    </form>
  )
}
