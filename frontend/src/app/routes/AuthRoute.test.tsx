// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import {
  createMemoryRouter,
  RouterProvider,
  useLocation,
} from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthContextValue } from '@/app/providers/auth/auth.types'

const authState = vi.hoisted(() => ({
  value: null as AuthContextValue | null,
}))

vi.mock('@/app/providers/auth/useAuth', () => ({
  useAuth: () => {
    if (!authState.value) {
      throw new Error('Auth state not configured for test')
    }

    return authState.value
  },
}))

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

  render(<RouterProvider router={router} />)
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

  render(<RouterProvider router={router} />)
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
})
