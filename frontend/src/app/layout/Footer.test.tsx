// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Footer } from './Footer'

describe('Footer', () => {
  it('apresenta o contexto do projeto e o repositório oficial', () => {
    render(<Footer />)

    expect(
      screen.getByText(/Hackathon Oracle Next Education G9/i),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', {
        name: 'Abrir repositório oficial do EnergiAI no GitHub',
      }),
    ).toHaveAttribute(
      'href',
      'https://github.com/No-Country-simulation/g9-br-team-09',
    )
  })
})
