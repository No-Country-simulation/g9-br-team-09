// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { AuthContextValue } from '@/app/providers/auth/auth.types'
import { AuthContext } from '@/app/providers/auth/AuthContext'

import { RegisterPage } from './RegisterPage'

describe('RegisterPage', () => {
  it('renderiza o formulário de cadastro', () => {
    const value: AuthContextValue = {
      status: 'anonymous',
      user: null,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      restoreSession: vi.fn(),
    }

    render(
      <MemoryRouter>
        <AuthContext value={value}>
          <RegisterPage />
        </AuthContext>
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', { name: 'Criar conta' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Voltar para o início' }),
    ).toHaveAttribute('href', '/')
  })
})
