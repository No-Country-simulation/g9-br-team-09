import type { Locator, Page } from '@playwright/test'

export const VALID_ANALYSIS = {
  applianceCount: '10',
  monthlyConsumption: '420',
  peakConsumptionHours: '8',
  propertyType: 'Casa',
  peakUsage: 'Sim',
} as const

export async function submitEnergyAnalysis(
  page: Page,
  monthlyConsumption = VALID_ANALYSIS.monthlyConsumption,
): Promise<void> {
  await page.getByRole('radio', { name: VALID_ANALYSIS.propertyType }).check()
  await page.getByRole('button', { name: 'Próximo' }).click()

  await page.getByRole('spinbutton').fill(VALID_ANALYSIS.applianceCount)
  await page.getByRole('button', { name: 'Próximo' }).click()

  await page.getByRole('spinbutton').fill(monthlyConsumption)
  await page.getByRole('button', { name: 'Próximo' }).click()

  await page.getByRole('radio', { name: VALID_ANALYSIS.peakUsage }).check()
  await page.getByRole('button', { name: 'Próximo' }).click()

  await page.getByRole('spinbutton').fill(VALID_ANALYSIS.peakConsumptionHours)
  const analysisResponsePromise = page.waitForResponse((response) => {
    const request = response.request()
    return (
      request.method() === 'POST' &&
      new URL(response.url()).pathname.endsWith('/analise-energetica')
    )
  })

  await page.getByRole('button', { name: 'Gerar análise' }).click()
  const analysisResponse = await analysisResponsePromise

  if (!analysisResponse.ok()) {
    throw new Error(
      `A análise iniciada pela interface falhou com HTTP ${analysisResponse.status()}.`,
    )
  }

  await page.waitForURL(/\/resultado$/)
}

export async function dashboardTotal(page: Page): Promise<number> {
  const emptyState = page.getByRole('heading', {
    name: 'Nenhuma análise realizada ainda',
  })
  const totalCard = page.getByRole('region', { name: 'Total de análises' })

  await emptyState.or(totalCard).waitFor()
  if (await emptyState.isVisible()) return 0

  return numericContent(totalCard)
}

export async function numericContent(locator: Locator): Promise<number> {
  const content = await locator.textContent()
  const match = content?.match(/\d+/)

  if (!match) throw new Error('Valor numerico nao encontrado na interface.')
  return Number(match[0])
}
