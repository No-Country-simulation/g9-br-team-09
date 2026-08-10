// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  clearCsrfToken,
  getCsrfToken,
  setCsrfToken,
} from '@/features/auth/session/csrf-token-manager'
import {
  clearSession,
  setSessionInvalidHandler,
} from '@/features/auth/session/session-manager'
import {
  clearAccessToken,
  getAccessToken,
} from '@/features/auth/session/token-manager'

vi.mock('@/features/auth/api/auth-api', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  refresh: vi.fn(),
  register: vi.fn(),
}))

import { logout, refresh } from '@/features/auth/api/auth-api'
import type { AuthenticationResponse } from '@/features/auth/schemas/responses'

import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

const authentication: AuthenticationResponse = {
  access_token: 'test-access-token',
  token_type: 'Bearer',
  expires_in: 900,
  usuario: {
    id: 1,
    nome: 'Test User',
    email: 'user@example.test',
    role: 'USER',
    criado_em: '2026-08-10T00:00:00',
  },
}

const refreshMock = vi.mocked(refresh)
const logoutMock = vi.mocked(logout)

function AuthProbe() {
  const { status, user, logout: signOut } = useAuth()

  return (
    <>
      <output data-testid="status">{status}</output>
      <output data-testid="user">{user?.email ?? 'none'}</output>
      <button onClick={() => void signOut().catch(() => undefined)}>
        Sair
      </button>
    </>
  )
}

describe('AuthProvider', () => {
  beforeEach(() => {
    setSessionInvalidHandler(null)
    clearSession()
    clearAccessToken()
    clearCsrfToken()
    refreshMock.mockReset()
    logoutMock.mockReset()
  })

  it('restaura a sessão e atualiza usuário e access token', async () => {
    refreshMock.mockResolvedValueOnce(authentication)

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )

    expect(screen.getByTestId('status')).toHaveTextContent('loading')

    await waitFor(() => {
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    })

    expect(screen.getByTestId('user')).toHaveTextContent('user@example.test')
    expect(getAccessToken()).toBe('test-access-token')
  })

  it('mantém uma única renovação efetiva no StrictMode', async () => {
    refreshMock.mockResolvedValueOnce(authentication)

    render(
      <StrictMode>
        <AuthProvider>
          <AuthProbe />
        </AuthProvider>
      </StrictMode>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    })

    expect(refreshMock).toHaveBeenCalledTimes(1)
  })

  it('trata ausência de sessão como estado anônimo sem erro global', async () => {
    refreshMock.mockRejectedValueOnce(new Error('no refresh cookie'))

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous')
    })

    expect(getAccessToken()).toBeNull()
  })

  it('encerra a sessão local mesmo quando logout falha', async () => {
    refreshMock.mockResolvedValueOnce(authentication)
    logoutMock.mockRejectedValueOnce(new Error('network failure'))

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    })

    setCsrfToken('old-csrf-token')
    fireEvent.click(screen.getByRole('button', { name: 'Sair' }))

    await waitFor(() => {
      expect(getAccessToken()).toBeNull()
    })

    expect(getCsrfToken()).toBeNull()
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous')
    expect(screen.getByTestId('user')).toHaveTextContent('none')
  })
})
