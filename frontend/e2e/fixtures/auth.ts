import { randomUUID } from 'node:crypto'

import type { Page } from '@playwright/test'

export async function authenticateDisposableUser(page: Page): Promise<void> {
  const uniqueId = randomUUID()
  const email = `e2e-${uniqueId}@example.com`
  const password = `E2e!${randomUUID()}`

  await page.goto('/')
  await page.getByRole('button', { name: 'Cadastre-se' }).click()
  await page
    .getByRole('textbox', { name: 'Nome completo' })
    .fill(`E2E ${uniqueId}`)
  await page.getByRole('textbox', { name: 'Email' }).fill(email)
  await page.getByLabel('Senha', { exact: true }).fill(password)
  await page.getByLabel('Confirme a senha').fill(password)
  await page.getByRole('button', { name: 'Criar conta' }).click()

  await page.waitForURL(/\/analise-energetica$/)
}
