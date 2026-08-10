import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { authHttpClient } from '@/shared/api/httpClient'

import { clearCsrfToken, getCsrfToken } from '../session/csrf-token-manager'
import { refresh, register } from './auth-api'

const registrationResponse = {
  id: 1,
  nome: 'Test User',
  email: 'user@example.test',
  role: 'USER' as const,
  criado_em: '2026-08-10T00:00:00',
}

function httpError(status: number, headers: Record<string, string> = {}) {
  return new AxiosError(
    'Request failed',
    undefined,
    undefined,
    undefined,
    {
      data: {},
      status,
      statusText: 'Error',
      headers,
      config: { headers: new AxiosHeaders() },
    },
  )
}

describe('auth api', () => {
  const postSpy = vi.spyOn(authHttpClient, 'post')

  afterEach(() => {
    postSpy.mockReset()
    clearCsrfToken()
  })

  it('mapeia o cadastro sem enviar confirmPassword', async () => {
    postSpy.mockResolvedValueOnce({
      data: registrationResponse,
    } as AxiosResponse)

    await register({
      fullName: 'Test User',
      email: 'user@example.test',
      password: 'password123',
      confirmPassword: 'password123',
    })

    expect(postSpy).toHaveBeenCalledWith('/auth/register', {
      nome: 'Test User',
      email: 'user@example.test',
      senha: 'password123',
    })
  })

  it('faz no máximo uma repetição para bootstrap CSRF', async () => {
    postSpy
      .mockRejectedValueOnce(
        httpError(403, { 'x-xsrf-token': 'bootstrap-csrf-token' }),
      )
      .mockRejectedValueOnce(httpError(401))

    await expect(refresh()).rejects.toBeInstanceOf(AxiosError)

    expect(postSpy).toHaveBeenCalledTimes(2)
    expect(postSpy).toHaveBeenNthCalledWith(
      1,
      '/auth/refresh',
      undefined,
      undefined,
    )
    expect(postSpy).toHaveBeenNthCalledWith(
      2,
      '/auth/refresh',
      undefined,
      {
        headers: { 'X-XSRF-TOKEN': 'bootstrap-csrf-token' },
      },
    )
    expect(getCsrfToken()).toBe('bootstrap-csrf-token')
  })
})
