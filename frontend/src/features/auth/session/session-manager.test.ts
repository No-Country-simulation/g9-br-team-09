import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/deferred'

vi.mock('../api/auth-api', () => ({
  refresh: vi.fn(),
}))

import { refresh } from '../api/auth-api'
import type { AuthenticationResponse } from '../schemas/responses'
import { getCsrfToken, setCsrfToken } from './csrf-token-manager'
import {
  clearSession,
  refreshAccessToken,
  setSessionInvalidHandler,
} from './session-manager'
import { getAccessToken, setAccessToken } from './token-manager'

const authentication: AuthenticationResponse = {
  access_token: 'test-access-token',
  token_type: 'Bearer',
  expires_in: 900,
  usuario: {
    id: 1,
    nome: 'Test User',
    email: 'user@example.test',
    role: 'USER',
    criado_em: '2026-08-10T00:00:00',
  },
}

const refreshMock = vi.mocked(refresh)

describe('session manager', () => {
  beforeEach(() => {
    setSessionInvalidHandler(null)
    clearSession()
    refreshMock.mockReset()
  })

  it('limpa access token, CSRF e notifica a sessão inválida', () => {
    const onInvalid = vi.fn()
    setAccessToken('old-access-token')
    setCsrfToken('old-csrf-token')
    setSessionInvalidHandler(onInvalid)

    clearSession()

    expect(getAccessToken()).toBeNull()
    expect(getCsrfToken()).toBeNull()
    expect(onInvalid).toHaveBeenCalledTimes(1)
  })

  it('compartilha uma única renovação entre operações concorrentes', async () => {
    const controlled = deferred<AuthenticationResponse>()
    refreshMock.mockReturnValueOnce(controlled.promise)

    const first = refreshAccessToken()
    const second = refreshAccessToken()
    const third = refreshAccessToken()

    expect(first).toBe(second)
    expect(second).toBe(third)
    expect(refreshMock).toHaveBeenCalledTimes(1)

    controlled.resolve(authentication)

    await expect(Promise.all([first, second, third])).resolves.toEqual([
      authentication,
      authentication,
      authentication,
    ])
    expect(getAccessToken()).toBe('test-access-token')
  })

  it('encerra a sessão quando o refresh falha', async () => {
    const onInvalid = vi.fn()
    setAccessToken('old-access-token')
    setCsrfToken('old-csrf-token')
    setSessionInvalidHandler(onInvalid)
    refreshMock.mockRejectedValueOnce(new Error('network failure'))

    await expect(refreshAccessToken()).rejects.toThrow('network failure')

    expect(getAccessToken()).toBeNull()
    expect(getCsrfToken()).toBeNull()
    expect(onInvalid).toHaveBeenCalledTimes(1)
  })

  it('não restaura o token quando logout ocorre durante o refresh', async () => {
    const controlled = deferred<AuthenticationResponse>()
    refreshMock.mockReturnValueOnce(controlled.promise)

    const refreshInFlight = refreshAccessToken()
    clearSession()
    controlled.resolve(authentication)

    await expect(refreshInFlight).rejects.toThrow(
      'A sessão foi encerrada durante a renovação.',
    )
    expect(getAccessToken()).toBeNull()
  })
})
