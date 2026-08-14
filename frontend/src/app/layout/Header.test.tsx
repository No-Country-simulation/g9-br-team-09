// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
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

function CurrentPath() {
  return <output>{useLocation().pathname}</output>
}

function renderHeader(initialPath = '/') {
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Header />
      <CurrentPath />
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

    expect(
      screen.getByRole('button', { name: 'Ir para o início' }),
    ).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Cadastre-se' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Começar análise' }),
    ).toBeNull()
    expect(screen.queryByRole('button', { name: 'Abrir painel' })).toBeNull()
    expect(
      screen.queryByRole('button', { name: 'Abrir histórico de análises' }),
    ).toBeNull()
  })

  it('mostra a navegação da aplicação e saída para usuário autenticado', () => {
    authState.value = auth('authenticated')

    renderHeader()

    expect(
      screen.getByRole('button', { name: 'Ir para o início' }),
    ).toBeInTheDocument()
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

  it.each([
    ['/', 'Ir para o início'],
    ['/painel', 'Abrir painel'],
    ['/historico', 'Abrir histórico de análises'],
    ['/analise-energetica', 'Iniciar nova análise energética'],
    ['/detalhes/42', 'Abrir histórico de análises'],
    ['/resultado', 'Iniciar nova análise energética'],
  ])('sinaliza somente a rota ativa em %s', (path, activeAction) => {
    authState.value = auth('authenticated')

    renderHeader(path)

    expect(screen.getByRole('button', { name: activeAction })).toHaveAttribute(
      'aria-current',
      'page',
    )
    const navigationActions = [
      'Ir para o início',
      'Abrir painel',
      'Abrir histórico de análises',
      'Iniciar nova análise energética',
    ]
    navigationActions
      .filter((action) => action !== activeAction)
      .forEach((action) => {
        expect(screen.getByRole('button', { name: action })).not.toHaveAttribute(
          'aria-current',
        )
      })
  })

  it('navega para a página inicial pelo logo', () => {
    authState.value = auth('authenticated')

    renderHeader('/historico')

    fireEvent.click(
      screen.getByRole('link', { name: 'Ir para a página inicial do EnergiAI' }),
    )

    expect(screen.getByRole('status')).toHaveTextContent('/')
  })

  it('encerra a sessão e redireciona para o login', async () => {
    const state = auth('authenticated')
    authState.value = state

    renderHeader('/painel')

    fireEvent.click(screen.getByRole('button', { name: 'Encerrar sessão' }))

    await waitFor(() => expect(state.logout).toHaveBeenCalledOnce())
    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('/login'),
    )
  })
})
