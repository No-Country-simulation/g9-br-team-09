// @vitest-environment jsdom

import { render, screen } from '@testing-library/react'
import { Zap } from 'lucide-react'
import { describe, expect, it, vi } from 'vitest'

import { FormStep } from './FormStep'

describe('FormStep', () => {
  it('desabilita o envio e informa o carregamento enquanto a análise é enviada', () => {
    render(
      <FormStep
        id="monthlyConsumption"
        icon={Zap}
        title="Consumo"
        question="Qual é o consumo mensal?"
        field={{
          kind: 'input',
          inputProps: { type: 'number' },
        }}
        currentStep={5}
        totalSteps={5}
        defaultValue="250"
        hideBackButton
        isSubmitting
        onBack={vi.fn()}
        onNext={vi.fn()}
        submitButtonProps={{ label: 'Enviar análise' }}
      />,
    )

    expect(
      screen.getByRole('button', { name: /Enviando/ }),
    ).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('Enviando análise...')
  })
})
