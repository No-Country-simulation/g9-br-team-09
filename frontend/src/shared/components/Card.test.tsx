// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { Activity } from 'lucide-react'
import { describe, expect, it } from 'vitest'

import { Card } from './Card'

describe('Card', () => {
  it('explica a semântica do índice de ineficiência sem alterar o valor', () => {
    render(
      <Card icon={Activity} label="Categoria" score={72}>
        Ineficiente
      </Card>,
    )

    expect(
      screen.getByText('Índice de ineficiência: 72/100'),
    ).toHaveAttribute(
      'title',
      '0 representa menor ineficiência; 100, maior ineficiência.',
    )
  })
})
