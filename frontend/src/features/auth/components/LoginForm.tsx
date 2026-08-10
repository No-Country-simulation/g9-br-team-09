import { Link, useLocation, useNavigate } from 'react-router-dom'

import { useAuth } from '@/app/providers/auth/useAuth'
import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'

import { getLoginFormErrorMessage } from '../api/auth-api'
import { useAuthForm } from '../hooks/useAuthForm'
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
          className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
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
        disabled={isSubmitting}
        className="mt-2 w-full"
      >
        {isSubmitting ? 'Entrando...' : 'Entrar'}
      </Button>

      <p className="text-muted-foreground text-center text-sm">
        Não possui uma conta?{' '}
        <Link to="/cadastro" className="text-primary font-medium">
          Cadastre-se
        </Link>
      </p>
    </form>
  )
}

function getPostLoginPath(state: unknown): string {
  if (!state || typeof state !== 'object' || !('from' in state)) {
    return '/analise-energetica'
  }

  const from = state.from
  if (!from || typeof from !== 'object' || !('pathname' in from)) {
    return '/analise-energetica'
  }

  const {
    pathname,
    search = '',
    hash = '',
  } = from as {
    pathname?: unknown
    search?: unknown
    hash?: unknown
  }

  if (
    typeof pathname !== 'string' ||
    !pathname.startsWith('/') ||
    pathname.startsWith('//')
  ) {
    return '/analise-energetica'
  }

  return `${pathname}${typeof search === 'string' ? search : ''}${typeof hash === 'string' ? hash : ''}`
}
