import {
  AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

import { httpClient } from '@/shared/api/httpClient'
import { deferred } from '@/test/deferred'

vi.mock('../api/auth-api', () => ({
  refresh: vi.fn(),
}))

import { refresh } from '../api/auth-api'
import type { AuthenticationResponse } from '../schemas/responses'
import { installAuthInterceptors } from './auth-interceptors'
import { clearSession, setSessionInvalidHandler } from './session-manager'
import { clearAccessToken, setAccessToken } from './token-manager'

const refreshMock = vi.mocked(refresh)

const authentication: AuthenticationResponse = {
  access_token: 'new-access-token',
  token_type: 'Bearer',
  expires_in: 900,
  usuario: {
    id: 1,
    nome: 'Test User',
    email: 'user@example.test',
    role: 'USER',
    criado_em: '2026-08-10T00:00:00Z',
  },
}

function unauthorized(config: InternalAxiosRequestConfig) {
  return new AxiosError('Unauthorized', undefined, config, undefined, {
    data: {},
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
  })
}

function success(config: InternalAxiosRequestConfig): AxiosResponse {
  return {
    data: { ok: true },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }
}

describe('auth interceptors', () => {
  beforeAll(() => {
    installAuthInterceptors()
  })

  beforeEach(() => {
    setSessionInvalidHandler(null)
    clearSession()
    clearAccessToken()
    refreshMock.mockReset()
  })

  it('renova após 401 e repete a request uma única vez com novo Bearer', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setAccessToken('old-access-token')
    refreshMock.mockResolvedValue(authentication)
    httpClient.defaults.adapter = async (config) => {
      requests.push(config)

      if (!('_retry' in config) || !config._retry) {
        throw unauthorized(config)
      }

      return success(config)
    }

    await expect(httpClient.get('/analise-energetica')).resolves.toMatchObject({
      data: { ok: true },
    })

    expect(refreshMock).toHaveBeenCalledTimes(1)
    expect(requests).toHaveLength(2)
    expect(requests[1]?.headers.Authorization).toBe('Bearer new-access-token')
  })

  it('não inicia um segundo refresh quando o retry retorna 401', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setAccessToken('old-access-token')
    refreshMock.mockResolvedValue(authentication)
    httpClient.defaults.adapter = async (config) => {
      requests.push(config)
      throw unauthorized(config)
    }

    await expect(httpClient.get('/analise-energetica')).rejects.toBeInstanceOf(
      AxiosError,
    )

    expect(refreshMock).toHaveBeenCalledTimes(1)
    expect(requests).toHaveLength(2)
  })

  it('não tenta refresh para endpoints de autenticação', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setAccessToken('test-access-token')
    httpClient.defaults.adapter = async (config) => {
      requests.push(config)
      throw unauthorized(config)
    }

    await Promise.all(
      ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'].map(
        async (endpoint) => {
          await expect(httpClient.post(endpoint)).rejects.toBeInstanceOf(
            AxiosError,
          )
        },
      ),
    )

    expect(refreshMock).not.toHaveBeenCalled()
    expect(requests).toHaveLength(4)
    expect(
      requests.every((request) => request.headers.Authorization === undefined),
    ).toBe(true)
  })

  it('compartilha um único refresh para três respostas 401 simultâneas', async () => {
    const refreshCompletion = deferred<void>()
    const refreshStarted = deferred<void>()
    const initialRequests = deferred<void>()
    const requests: InternalAxiosRequestConfig[] = []
    let initialRequestCount = 0

    setAccessToken('old-access-token')
    refreshMock.mockImplementation(async () => {
      refreshStarted.resolve()
      await refreshCompletion.promise
      return authentication
    })
    httpClient.defaults.adapter = async (config) => {
      requests.push(config)

      if (!('_retry' in config) || !config._retry) {
        initialRequestCount += 1
        if (initialRequestCount === 3) {
          initialRequests.resolve()
        }
        throw unauthorized(config)
      }

      return success(config)
    }

    const responses = [
      httpClient.get('/analise-energetica'),
      httpClient.get('/historico'),
      httpClient.get('/painel'),
    ]

    await initialRequests.promise
    await refreshStarted.promise

    refreshCompletion.resolve()

    await expect(Promise.all(responses)).resolves.toHaveLength(3)
    expect(refreshMock).toHaveBeenCalledTimes(1)
    expect(requests).toHaveLength(6)
  })
})
