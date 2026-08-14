// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { LoadingSpinner } from './LoadingSpinner'

describe('LoadingSpinner', () => {
  it('anuncia o carregamento com um rótulo contextual', () => {
    render(<LoadingSpinner label="Carregando sessão..." />)

    expect(screen.getByRole('status')).toHaveTextContent('Carregando sessão...')
  })

  it('preserva o rótulo acessível quando o texto visual está oculto', () => {
    render(<LoadingSpinner label="Enviando análise..." showLabel={false} />)

    expect(screen.getByRole('status')).toHaveTextContent('Enviando análise...')
  })
})
