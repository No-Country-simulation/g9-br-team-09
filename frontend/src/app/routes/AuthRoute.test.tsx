// @vitest-environment jsdom

import { fireEvent, render, screen } from '@testing-library/react'
import { type ReactNode, useState } from 'react'
import {
  createMemoryRouter,
  RouterProvider,
  useLocation,
} from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthContextValue } from '@/app/providers/auth/auth.types'
import { AuthContext } from '@/app/providers/auth/AuthContext'
import { LoginForm } from '@/features/auth/components/LoginForm'

const authState = {
  value: null as AuthContextValue | null,
}

import { ProtectedRoute, PublicOnlyRoute } from './AuthRoute'

function auth(status: AuthContextValue['status']): AuthContextValue {
  return {
    status,
    user:
      status === 'authenticated'
        ? {
            id: 1,
            nome: 'Test User',
            email: 'user@example.test',
            role: 'USER',
          }
        : null,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    restoreSession: vi.fn(),
  }
}

function LoginLocation() {
  const location = useLocation()
  const from = (location.state as { from?: { pathname?: string } } | null)?.from

  return <p>login from {from?.pathname ?? 'none'}</p>
}

function renderProtectedRoute(initialPath = '/historico') {
  const router = createMemoryRouter(
    [
      {
        element: <ProtectedRoute />,
        children: [
          {
            path: '/historico',
            element: <p>conteúdo privado</p>,
          },
        ],
      },
      { path: '/login', element: <LoginLocation /> },
    ],
    { initialEntries: [initialPath] },
  )

  render(
    <AuthContext value={authState.value}>
      <RouterProvider router={router} />
    </AuthContext>,
  )
}

function renderPublicOnlyRoute() {
  const router = createMemoryRouter(
    [
      {
        element: <PublicOnlyRoute />,
        children: [{ path: '/login', element: <p>login público</p> }],
      },
      { path: '/analise-energetica', element: <p>análise protegida</p> },
    ],
    { initialEntries: ['/login'] },
  )

  render(
    <AuthContext value={authState.value}>
      <RouterProvider router={router} />
    </AuthContext>,
  )
}

function AuthHarness({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthContextValue['status']>('anonymous')

  const value: AuthContextValue = {
    status,
    user:
      status === 'authenticated'
        ? {
            id: 1,
            nome: 'Test User',
            email: 'user@example.test',
            role: 'USER',
          }
        : null,
    login: async () => {
      setStatus('authenticated')
    },
    register: vi.fn(),
    logout: vi.fn(),
    restoreSession: vi.fn(),
  }

  return <AuthContext value={value}>{children}</AuthContext>
}

function CurrentLocation() {
  const location = useLocation()

  return <p>{`${location.pathname}${location.search}`}</p>
}

function renderPostLoginFlow(initialPath = '/historico') {
  const router = createMemoryRouter(
    [
      {
        element: <PublicOnlyRoute />,
        children: [{ path: '/login', element: <LoginForm /> }],
      },
      {
        element: <ProtectedRoute />,
        children: [
          {
            path: '/historico',
            element: <CurrentLocation />,
          },
        ],
      },
      {
        path: '/analise-energetica',
        element: <p>análise protegida</p>,
      },
    ],
    { initialEntries: [initialPath] },
  )

  render(
    <AuthHarness>
      <RouterProvider router={router} />
    </AuthHarness>,
  )
}

describe('auth routes', () => {
  beforeEach(() => {
    authState.value = null
  })

  it('não renderiza nem redireciona rota privada durante loading', () => {
    authState.value = auth('loading')

    renderProtectedRoute()

    expect(screen.getByRole('status')).toHaveTextContent('Carregando sessão...')
    expect(screen.queryByText('conteúdo privado')).not.toBeInTheDocument()
    expect(screen.queryByText(/login from/)).not.toBeInTheDocument()
  })

  it('renderiza rota privada para usuário autenticado', () => {
    authState.value = auth('authenticated')

    renderProtectedRoute()

    expect(screen.getByText('conteúdo privado')).toBeInTheDocument()
  })

  it('redireciona visitante e preserva a rota interna solicitada', async () => {
    authState.value = auth('anonymous')

    renderProtectedRoute()

    expect(await screen.findByText('login from /historico')).toBeInTheDocument()
  })

  it('mantém login público em loading', () => {
    authState.value = auth('loading')
    renderPublicOnlyRoute()

    expect(screen.getByRole('status')).toHaveTextContent('Carregando sessão...')
  })

  it('permite login para visitante', () => {
    authState.value = auth('anonymous')
    renderPublicOnlyRoute()

    expect(screen.getByText('login público')).toBeInTheDocument()
  })

  it('redireciona usuário autenticado de login para rota interna padrão', async () => {
    authState.value = auth('authenticated')

    renderPublicOnlyRoute()

    expect(await screen.findByText('análise protegida')).toBeInTheDocument()
  })

  it('retorna à rota protegida originalmente solicitada após login', async () => {
    renderPostLoginFlow('/historico?page=2')

    fireEvent.change(await screen.findByLabelText('Email'), {
      target: { value: 'user@example.test' },
    })
    fireEvent.change(screen.getByLabelText('Senha'), {
      target: { value: 'password123' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('/historico?page=2')).toBeInTheDocument()
    expect(screen.queryByText('análise protegida')).not.toBeInTheDocument()
  })
})
