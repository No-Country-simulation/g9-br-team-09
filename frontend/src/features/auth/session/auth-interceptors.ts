import { type InternalAxiosRequestConfig, isAxiosError } from 'axios'

import { httpClient } from '@/shared/api/httpClient'

import { refreshAccessToken } from './session-manager'
import { getAccessToken } from './token-manager'

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const AUTH_ENDPOINTS = new Set([
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/logout',
])

let areInterceptorsInstalled = false

function isAuthEndpoint(url: string | undefined): boolean {
  const path = url?.split('?')[0]
  return path !== undefined && AUTH_ENDPOINTS.has(path)
}

export function installAuthInterceptors(): void {
  if (areInterceptorsInstalled) {
    return
  }

  areInterceptorsInstalled = true

  httpClient.interceptors.request.use((config) => {
    const accessToken = getAccessToken()

    if (accessToken && !isAuthEndpoint(config.url)) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }

    return config
  })

  httpClient.interceptors.response.use(undefined, async (error: unknown) => {
    if (!isAxiosError(error)) {
      return Promise.reject(error)
    }

    const requestConfig = error.config as RetriableRequestConfig | undefined

    if (
      error.response?.status !== 401 ||
      !requestConfig ||
      requestConfig._retry ||
      !getAccessToken() ||
      isAuthEndpoint(requestConfig.url)
    ) {
      return Promise.reject(error)
    }

    requestConfig._retry = true

    try {
      await refreshAccessToken()
      return httpClient(requestConfig)
    } catch {
      return Promise.reject(error)
    }
  })
}
