// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
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

vi.mock('@/app/providers/theme', () => ({
  useTheme: () => ({ theme: 'light', toggleTheme: vi.fn() }),
}))

import { Header } from './Header'

function auth(status: AuthContextValue['status']): AuthContextValue {
  return {
    status,
    user: null,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    restoreSession: vi.fn(),
  }
}

function renderHeader() {
  render(
    <MemoryRouter>
      <Header />
    </MemoryRouter>,
  )
}

describe('Header', () => {
  beforeEach(() => {
    authState.value = null
  })

  it('mostra as ações de acesso e início para visitante', () => {
    authState.value = auth('anonymous')

    renderHeader()

    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Cadastre-se' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Começar análise' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Abrir painel' })).toBeNull()
    expect(
      screen.queryByRole('button', { name: 'Abrir histórico de análises' }),
    ).toBeNull()
  })

  it('mostra a navegação da aplicação e saída para usuário autenticado', () => {
    authState.value = auth('authenticated')

    renderHeader()

    expect(
      screen.getByRole('button', { name: 'Abrir painel' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Abrir histórico de análises' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Iniciar nova análise energética' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Encerrar sessão' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Entrar' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Cadastre-se' })).toBeNull()
  })
})
