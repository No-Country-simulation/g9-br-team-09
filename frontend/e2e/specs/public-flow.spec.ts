import process from 'node:process'

import type { Request } from '@playwright/test'

import { expect, test } from '../fixtures/app-test'
import { authenticateDisposableUser } from '../fixtures/auth'
import {
  dashboardTotal,
  numericContent,
  submitEnergyAnalysis,
} from '../fixtures/energy-analysis'

const SOURCE_LABELS = {
  ML_MODEL: 'Modelo preditivo',
  RULE_BASED_FALLBACK: 'Critério de reserva',
} as const

const expectedSource =
  (process.env.E2E_EXPECTED_SOURCE as keyof typeof SOURCE_LABELS | undefined) ??
  'ML_MODEL'

test('carrega a aplicação e bloqueia dados inválidos no formulário', async ({
  page,
}) => {
  await page.goto('/')

  await expect(
    page.getByRole('heading', {
      name: 'Entenda o perfil de consumo do seu imóvel',
    }),
  ).toBeVisible()
  await expect(
    page.getByRole('main').getByRole('button', { name: 'Começar análise' }),
  ).toBeVisible()

  await authenticateDisposableUser(page)
  await expect(
    page.getByRole('group', {
      name: 'Qual é o tipo do imóvel que será analisado?',
    }),
  ).toBeVisible()

  await page.getByRole('radio', { name: 'Casa' }).check()
  await page.getByRole('button', { name: 'Próximo' }).click()

  let analysisRequests = 0
  const countAnalysisRequests = (request: Request) => {
    if (
      request.method() === 'POST' &&
      new URL(request.url()).pathname.endsWith('/analise-energetica')
    ) {
      analysisRequests += 1
    }
  }
  page.on('request', countAnalysisRequests)

  await page.getByRole('spinbutton').fill('0')
  await page.getByRole('button', { name: 'Próximo' }).click()

  await expect(page.getByRole('alert')).toContainText(
    'A quantidade de equipamentos deve ser maior ou igual a 1',
  )
  await expect(
    page.getByRole('group', {
      name: 'Quantos equipamentos elétricos existem no imóvel?',
    }),
  ).toBeVisible()
  expect(analysisRequests).toBe(0)
  page.off('request', countAnalysisRequests)
})

test('valida análise, persistência, histórico, detalhes, painel e navegação', async ({
  context,
  page,
}, testInfo) => {
  await authenticateDisposableUser(page)

  await page.getByRole('button', { name: 'Abrir painel' }).click()
  await expect(
    page.getByRole('heading', { name: 'Painel de análises' }),
  ).toBeVisible()
  const totalBefore = await dashboardTotal(page)

  await page
    .getByRole('button', { name: 'Iniciar nova análise energética' })
    .click()
  await submitEnergyAnalysis(page)

  await expect(
    page.getByRole('heading', { name: 'Resultado da análise' }),
  ).toBeVisible()

  const categoryCard = page.getByRole('region', { name: 'Categoria' })
  await expect(categoryCard).toContainText(/Eficiente|Moderado|Ineficiente/)
  const score = await numericContent(
    categoryCard.getByText(/Índice de ineficiência:/),
  )
  expect(score).toBeGreaterThanOrEqual(0)
  expect(score).toBeLessThanOrEqual(100)

  await expect(
    page.getByRole('region', { name: 'Custo estimado' }),
  ).toContainText(/R\$\s*315,00/)
  await expect(
    page.getByRole('region', { name: 'Método de análise' }),
  ).toContainText(SOURCE_LABELS[expectedSource])

  const probability = await numericContent(
    page.getByRole('img', { name: /Probabilidade:/ }),
  )
  expect(probability).toBeGreaterThanOrEqual(0)
  expect(probability).toBeLessThanOrEqual(100)

  const recommendations = page.getByRole('region', { name: 'Recomendações' })
  expect(await recommendations.getByRole('listitem').count()).toBeGreaterThan(0)
  await expect(
    page.getByText(/stack trace|exception|erro interno/i),
  ).toHaveCount(0)

  await testInfo.attach('observed-classification-source', {
    body: JSON.stringify({
      observed: expectedSource,
      publicFrontendUrl: testInfo.project.use.baseURL,
    }),
    contentType: 'application/json',
  })

  await page
    .getByRole('button', { name: 'Abrir histórico de análises' })
    .click()
  await expect(
    page.getByRole('heading', { name: 'Histórico de análises' }),
  ).toBeVisible()
  await expect(page.getByText(/R\$\s*315,00/).first()).toBeVisible()

  const detailsButton = page
    .getByRole('button', { name: /Ver detalhes da análise \d+/ })
    .first()
  const detailsButtonName = await detailsButton.getAttribute('aria-label')
  const analysisId = detailsButtonName?.match(/\d+$/)?.[0]
  expect(analysisId).toBeTruthy()
  await detailsButton.click()

  await expect(page).toHaveURL(new RegExp(`/detalhes/${analysisId}$`))
  await expect(
    page.getByRole('heading', {
      name: `Análise #${String(analysisId).padStart(2, '0')}`,
    }),
  ).toBeVisible()

  const analyzedData = page.getByRole('region', { name: 'Dados analisados' })
  await expect(analyzedData).toContainText('Casa')
  await expect(analyzedData).toContainText('10')
  await expect(analyzedData).toContainText(/420\s*kWh/)
  await expect(analyzedData).toContainText('Sim')
  await expect(analyzedData).toContainText('8')

  const persistedResult = page.getByRole('region', {
    name: 'Resultado da classificação energética',
  })
  await expect(persistedResult).toContainText(SOURCE_LABELS[expectedSource])
  await expect(persistedResult).toContainText(/R\$\s*315,00/)
  expect(await persistedResult.getByRole('listitem').count()).toBeGreaterThan(0)

  await page.reload()
  await expect(
    page.getByRole('heading', { name: new RegExp(`Análise #0*${analysisId}`) }),
  ).toBeVisible()

  await page.getByRole('button', { name: 'Abrir painel' }).click()
  const totalAfter = await dashboardTotal(page)
  expect(totalAfter).toBe(totalBefore + 1)

  await page
    .getByRole('button', { name: 'Iniciar nova análise energética' })
    .click()
  await submitEnergyAnalysis(page, '421')
  await expect(
    page.getByRole('heading', { name: 'Resultado da análise' }),
  ).toBeVisible()

  await page.reload()
  await expect(
    page.getByRole('heading', { name: 'Resultado da análise' }),
  ).toBeVisible()
  await expect(
    page.getByRole('region', { name: 'Método de análise' }),
  ).toContainText(SOURCE_LABELS[expectedSource])

  const directResultPage = await context.newPage()
  await directResultPage.goto('/resultado')
  await expect(
    directResultPage.getByRole('heading', {
      name: 'Nenhum resultado para exibir',
    }),
  ).toBeVisible()
  await directResultPage
    .getByRole('button', { name: 'Fazer nova análise' })
    .click()
  await expect(
    directResultPage.getByRole('group', {
      name: 'Qual é o tipo do imóvel que será analisado?',
    }),
  ).toBeVisible()
  await directResultPage.close()
})
