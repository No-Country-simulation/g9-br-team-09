// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { AuthContextValue } from '@/app/providers/auth/auth.types'
import { AuthContext } from '@/app/providers/auth/AuthContext'

import { LoginPage } from './LoginPage'

function renderLoginPage(state?: unknown) {
  const value: AuthContextValue = {
    status: 'anonymous',
    user: null,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    restoreSession: vi.fn(),
  }

  render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <AuthContext value={value}>
        <LoginPage />
      </AuthContext>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('explica que a análise será continuada após o login', () => {
    renderLoginPage({ from: { pathname: '/analise-energetica' } })

    expect(
      screen.getByText('Faça login para continuar sua análise energética.'),
    ).toBeInTheDocument()
  })

  it('não exibe mensagem de análise para outras origens', () => {
    renderLoginPage({ from: { pathname: '/historico' } })

    expect(
      screen.queryByText('Faça login para continuar sua análise energética.'),
    ).not.toBeInTheDocument()
  })
})
