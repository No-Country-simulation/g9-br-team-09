import process from 'node:process'

import { expect, test } from '../fixtures/app-test'
import { authenticateDisposableUser } from '../fixtures/auth'
import {
  numericContent,
  submitEnergyAnalysis,
} from '../fixtures/energy-analysis'

test('mantém a análise disponível durante fallback controlado', async ({
  page,
}) => {
  test.skip(
    process.env.E2E_EXPECTED_SOURCE !== 'RULE_BASED_FALLBACK',
    'Execute apenas durante a janela controlada de fallback.',
  )

  await authenticateDisposableUser(page)
  await submitEnergyAnalysis(page)

  await expect(
    page.getByRole('heading', { name: 'Resultado da análise' }),
  ).toBeVisible()
  const categoryCard = page.getByRole('region', { name: 'Categoria' })
  await expect(categoryCard).toContainText(
    /Eficiente|Moderado|Ineficiente/,
  )
  const score = await numericContent(
    categoryCard.getByText(/Índice de ineficiência:/),
  )
  expect(score).toBeGreaterThanOrEqual(0)
  expect(score).toBeLessThanOrEqual(100)
  await expect(
    page.getByRole('region', { name: 'Custo estimado' }),
  ).toBeVisible()
  await expect(
    page.getByRole('region', { name: 'Método de análise' }),
  ).toContainText('Critério de reserva')
  const probability = await numericContent(
    page.getByRole('img', { name: /Probabilidade:/ }),
  )
  expect(probability).toBeGreaterThanOrEqual(0)
  expect(probability).toBeLessThanOrEqual(100)
  expect(
    await page
      .getByRole('region', { name: 'Recomendações' })
      .getByRole('listitem')
      .count(),
  ).toBeGreaterThan(0)
  await expect(
    page.getByText(/stack trace|exception|erro interno/i),
  ).toHaveCount(0)
})
