import { afterEach, describe, expect, it } from 'vitest'

import {
  clearCsrfToken,
  getCsrfToken,
  setCsrfToken,
} from './csrf-token-manager'
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from './token-manager'

afterEach(() => {
  clearAccessToken()
  clearCsrfToken()
})

describe('token managers', () => {
  it('mantém o access token apenas no módulo em memória', () => {
    expect(getAccessToken()).toBeNull()

    setAccessToken('test-access-token')

    expect(getAccessToken()).toBe('test-access-token')

    clearAccessToken()

    expect(getAccessToken()).toBeNull()
  })

  it('mantém e limpa o token CSRF separadamente', () => {
    expect(getCsrfToken()).toBeNull()

    setCsrfToken('test-csrf-token')

    expect(getCsrfToken()).toBe('test-csrf-token')

    clearCsrfToken()

    expect(getCsrfToken()).toBeNull()
  })
})
