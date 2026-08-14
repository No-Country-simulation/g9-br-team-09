// @vitest-environment jsdom

import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { EnergyAnalysisHomePage } from './EnergyAnalysisHomePage'

function CurrentPath() {
  return <output>{useLocation().pathname}</output>
}

describe('EnergyAnalysisHomePage', () => {
  it('apresenta o funcionamento, os resultados e um CTA para a análise', () => {
    render(
      <MemoryRouter>
        <EnergyAnalysisHomePage />
        <CurrentPath />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', {
        name: 'Entenda o perfil de consumo do seu imóvel',
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('Consumo mensal em kWh')).toBeInTheDocument()
    expect(
      screen.getByText(/0 representa menor ineficiência e 100, maior/i),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Classificação com Machine Learning' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Entrar' })).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Começar análise' }))

    expect(screen.getByRole('status')).toHaveTextContent('/analise-energetica')
  })
})
