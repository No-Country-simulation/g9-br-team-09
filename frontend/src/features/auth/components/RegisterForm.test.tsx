// @vitest-environment jsdom

import { fireEvent, render, screen } from '@testing-library/react'
import { AxiosError, AxiosHeaders } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { AuthContextValue } from '@/app/providers/auth/auth.types'
import { AuthContext } from '@/app/providers/auth/AuthContext'

import { RegisterForm } from './RegisterForm'

function renderRegisterForm(register = vi.fn()) {
  const value: AuthContextValue = {
    status: 'anonymous',
    user: null,
    login: vi.fn(),
    register,
    logout: vi.fn(),
    restoreSession: vi.fn(),
  }

  render(
    <MemoryRouter>
      <AuthContext value={value}>
        <RegisterForm />
      </AuthContext>
    </MemoryRouter>,
  )

  return register
}

function fillValidForm() {
  fireEvent.change(screen.getByLabelText('Nome completo'), {
    target: { value: 'Test User' },
  })
  fireEvent.change(screen.getByLabelText('Email'), {
    target: { value: 'user@example.test' },
  })
  fireEvent.change(screen.getByLabelText('Senha'), {
    target: { value: 'password123' },
  })
  fireEvent.change(screen.getByLabelText('Confirme a senha'), {
    target: { value: 'password123' },
  })
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

describe('RegisterForm', () => {
  it('bloqueia o cadastro quando a confirmação de senha diverge', () => {
    const register = renderRegisterForm()

    fireEvent.change(screen.getByLabelText('Senha'), {
      target: { value: 'password123' },
    })
    fireEvent.change(screen.getByLabelText('Confirme a senha'), {
      target: { value: 'password456' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(register).not.toHaveBeenCalled()
    expect(screen.getByText('As senhas não coincidem.')).toBeInTheDocument()
  })

  it('envia valores válidos à camada de autenticação', async () => {
    const register = vi.fn().mockResolvedValueOnce(undefined)
    renderRegisterForm(register)
    fillValidForm()

    fireEvent.click(screen.getByRole('button', { name: 'Criar conta' }))

    await Promise.resolve()
    expect(register).toHaveBeenCalledWith({
      fullName: 'Test User',
      email: 'user@example.test',
      password: 'password123',
      confirmPassword: 'password123',
    })
  })

  it('exibe mensagem segura para e-mail já cadastrado', async () => {
    const register = vi.fn().mockRejectedValueOnce(httpError(409))
    renderRegisterForm(register)
    fillValidForm()

    fireEvent.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(
      await screen.findByText('Este e-mail já está cadastrado.'),
    ).toBeInTheDocument()
  })
})
