import { execFileSync } from 'node:child_process'
import process from 'node:process'

import { defineConfig, devices } from '@playwright/test'

const DEFAULT_REQUEST_TIMEOUT = 30_000
const ALLOWED_SOURCES = ['ML_MODEL', 'RULE_BASED_FALLBACK'] as const

function requiredBaseUrl(): string {
  const configuredUrl = process.env.E2E_BASE_URL?.trim()

  if (!configuredUrl) {
    throw new Error(
      'E2E_BASE_URL e obrigatoria. Informe a URL publica do frontend.',
    )
  }

  const url = new URL(configuredUrl)
  if (
    !['http:', 'https:'].includes(url.protocol) ||
    url.username ||
    url.password
  ) {
    throw new Error('E2E_BASE_URL deve ser uma URL HTTP(S) sem credenciais.')
  }

  if (url.search || url.hash) {
    throw new Error('E2E_BASE_URL nao deve conter query string ou fragmento.')
  }

  return url.toString().replace(/\/$/, '')
}

function requestTimeout(): number {
  const rawValue = process.env.E2E_REQUEST_TIMEOUT?.trim()
  if (!rawValue) return DEFAULT_REQUEST_TIMEOUT

  const parsedValue = Number(rawValue)
  if (!Number.isSafeInteger(parsedValue) || parsedValue <= 0) {
    throw new Error('E2E_REQUEST_TIMEOUT deve ser um inteiro positivo em ms.')
  }

  return parsedValue
}

function expectedSource(): (typeof ALLOWED_SOURCES)[number] {
  const source = process.env.E2E_EXPECTED_SOURCE?.trim() ?? 'ML_MODEL'
  if (!ALLOWED_SOURCES.includes(source as (typeof ALLOWED_SOURCES)[number])) {
    throw new Error(
      `E2E_EXPECTED_SOURCE deve ser ${ALLOWED_SOURCES.join(' ou ')}.`,
    )
  }

  return source as (typeof ALLOWED_SOURCES)[number]
}

function currentCommit(): string {
  if (process.env.GITHUB_SHA) return process.env.GITHUB_SHA

  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      encoding: 'utf8',
    }).trim()
  } catch {
    return 'indisponivel'
  }
}

const baseURL = requiredBaseUrl()
const timeout = requestTimeout()
const source = expectedSource()

export default defineConfig({
  testDir: './e2e/specs',
  outputDir: 'test-results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: timeout * 4,
  expect: { timeout },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  metadata: {
    commit: currentCommit(),
    environment: new URL(baseURL).hostname,
    executedAt: new Date().toISOString(),
    expectedClassificationSource: source,
    publicFrontendUrl: baseURL,
  },
  use: {
    baseURL,
    actionTimeout: timeout,
    navigationTimeout: timeout,
    screenshot: 'only-on-failure',
    trace: 'off',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
