// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AxiosError, AxiosHeaders } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { AuthContextValue } from '@/app/providers/auth/auth.types'
import { AuthContext } from '@/app/providers/auth/AuthContext'
import { deferred } from '@/test/deferred'

import { getPostLoginPath } from '../navigation/post-login-path'
import { LoginForm } from './LoginForm'

function renderLoginForm(login = vi.fn()) {
  const value: AuthContextValue = {
    status: 'anonymous',
    user: null,
    login,
    register: vi.fn(),
    logout: vi.fn(),
    restoreSession: vi.fn(),
  }

  render(
    <MemoryRouter>
      <AuthContext value={value}>
        <LoginForm />
      </AuthContext>
    </MemoryRouter>,
  )

  return login
}

function httpError(status: number) {
  return new AxiosError('Request failed', undefined, undefined, undefined, {
    data: {},
    status,
    statusText: 'Error',
    headers: {},
    config: { headers: new AxiosHeaders() },
  })
}

describe('LoginForm', () => {
  it('não chama login quando a validação local falha', () => {
    const login = renderLoginForm()

    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(login).not.toHaveBeenCalled()
    expect(screen.getByText('Informe um e-mail válido.')).toBeInTheDocument()
  })

  it('chama login com os valores válidos e bloqueia submit duplicado', async () => {
    const controlled = deferred<void>()
    const login = vi.fn(() => controlled.promise)
    renderLoginForm(login)

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'user@example.test' },
    })
    fireEvent.change(screen.getByLabelText('Senha'), {
      target: { value: 'password123' },
    })

    const form = screen.getByRole('button', { name: 'Entrar' }).closest('form')
    expect(form).not.toBeNull()
    fireEvent.submit(form!)
    fireEvent.submit(form!)

    await waitFor(() => {
      expect(login).toHaveBeenCalledTimes(1)
    })
    expect(login).toHaveBeenCalledWith({
      email: 'user@example.test',
      password: 'password123',
    })

    controlled.resolve()
  })

  it('exibe uma mensagem genérica para login 401', async () => {
    const login = vi.fn().mockRejectedValueOnce(httpError(401))
    renderLoginForm(login)

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'user@example.test' },
    })
    fireEvent.change(screen.getByLabelText('Senha'), {
      target: { value: 'password123' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(
      await screen.findByText('E-mail ou senha inválidos.'),
    ).toBeInTheDocument()
  })

  it('aceita somente destinos internos após login', () => {
    expect(
      getPostLoginPath({ from: { pathname: '/historico', search: '?page=2' } }),
    ).toBe('/historico?page=2')
    expect(getPostLoginPath({ from: { pathname: '/detalhes/1' } })).toBe(
      '/detalhes/1',
    )
    expect(getPostLoginPath({ from: { pathname: '//evil.example' } })).toBe(
      '/analise-energetica',
    )
    expect(getPostLoginPath({ from: { pathname: 'https://evil.example' } })).toBe(
      '/analise-energetica',
    )
    expect(getPostLoginPath({ from: { pathname: 'http://evil.example' } })).toBe(
      '/analise-energetica',
    )
  })
})
