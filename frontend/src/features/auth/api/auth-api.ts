import { type AxiosResponse,isAxiosError } from 'axios'

import {
  normalizeApiError,
  type NormalizedApiError,
} from '@/shared/api/api-error'
import { authHttpClient } from '@/shared/api/httpClient'

import type { LoginFormValues } from '../schemas/login'
import type { RegisterFormValues } from '../schemas/register'
import {
  authenticationResponseSchema,
  registrationResponseSchema,
} from '../schemas/responses'
import { getCsrfToken, setCsrfToken } from '../session/csrf-token-manager'
import type { LoginRequest, RegisterRequest } from '../types/auth'

const AUTH_RESOURCE = '/auth'

function toRegisterRequest({
  fullName,
  email,
  password,
}: RegisterFormValues): RegisterRequest {
  return {
    nome: fullName,
    email,
    senha: password,
  }
}

function toLoginRequest({ email, password }: LoginFormValues): LoginRequest {
  return {
    email,
    senha: password,
  }
}

export async function register(values: RegisterFormValues) {
  const response = await authHttpClient.post(
    `${AUTH_RESOURCE}/register`,
    toRegisterRequest(values),
  )

  return registrationResponseSchema.parse(response.data)
}

export async function login(values: LoginFormValues) {
  const response = await authHttpClient.post(
    `${AUTH_RESOURCE}/login`,
    toLoginRequest(values),
  )
  captureCsrfToken(response)

  return authenticationResponseSchema.parse(response.data)
}

export async function refresh() {
  try {
    return await requestRefresh()
  } catch (error) {
    captureCsrfTokenFromError(error)

    // The backend delivers a CSRF header with the first rejected refresh.
    // Retry once with the token captured from that response.
    if (isCsrfBootstrapError(error)) {
      return requestRefresh()
    }

    throw error
  }
}

async function requestRefresh() {
  const response = await authHttpClient.post(
    `${AUTH_RESOURCE}/refresh`,
    undefined,
    csrfRequestConfig(),
  )
  captureCsrfToken(response)

  return authenticationResponseSchema.parse(response.data)
}

export async function logout() {
  await authHttpClient.post(
    `${AUTH_RESOURCE}/logout`,
    undefined,
    csrfRequestConfig(),
  )
}

export function getRegisterFormErrorMessage(error: unknown): string {
  const normalizedError = normalizeApiError(error)

  if (normalizedError.kind === 'http' && normalizedError.status === 409) {
    return 'Este e-mail já está cadastrado.'
  }

  return getGenericFormErrorMessage(normalizedError)
}

export function getLoginFormErrorMessage(error: unknown): string {
  const normalizedError = normalizeApiError(error)

  if (normalizedError.kind === 'http' && normalizedError.status === 401) {
    return 'E-mail ou senha inválidos.'
  }

  return getGenericFormErrorMessage(normalizedError)
}

function getGenericFormErrorMessage(error: NormalizedApiError): string {
  if (error.kind === 'network') {
    return 'Não foi possível conectar ao servidor. Tente novamente.'
  }

  if (error.kind === 'http' && error.status === 400) {
    return 'Não foi possível concluir a operação. Verifique os dados informados.'
  }

  return 'Não foi possível concluir a operação. Tente novamente.'
}

function isCsrfBootstrapError(error: unknown): boolean {
  const normalizedError = normalizeApiError(error)

  return normalizedError.kind === 'http' && normalizedError.status === 403
}

function csrfRequestConfig() {
  const csrfToken = getCsrfToken()

  if (!csrfToken) {
    return undefined
  }

  return {
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
  }
}

function captureCsrfToken(response: AxiosResponse): void {
  const csrfToken = response.headers['x-xsrf-token']

  if (typeof csrfToken === 'string' && csrfToken.length > 0) {
    setCsrfToken(csrfToken)
  }
}

function captureCsrfTokenFromError(error: unknown): void {
  if (isAxiosError(error) && error.response) {
    captureCsrfToken(error.response)
  }
}
