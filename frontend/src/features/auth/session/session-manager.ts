import { refresh } from '../api/auth-api'
import type { AuthenticationResponse } from '../schemas/responses'
import { clearCsrfToken } from './csrf-token-manager'
import { clearAccessToken, setAccessToken } from './token-manager'

type SessionInvalidHandler = () => void

let refreshPromise: Promise<AuthenticationResponse> | null = null
let sessionInvalidHandler: SessionInvalidHandler | null = null
let sessionGeneration = 0

export function setSessionInvalidHandler(
  handler: SessionInvalidHandler | null,
): void {
  sessionInvalidHandler = handler
}

export function clearSession(): void {
  sessionGeneration += 1
  clearAccessToken()
  clearCsrfToken()
  sessionInvalidHandler?.()
}

export function refreshAccessToken(): Promise<AuthenticationResponse> {
  if (!refreshPromise) {
    const refreshGeneration = sessionGeneration

    refreshPromise = refresh()
      .then((authentication) => {
        if (refreshGeneration !== sessionGeneration) {
          throw new Error('A sessão foi encerrada durante a renovação.')
        }

        setAccessToken(authentication.access_token)
        return authentication
      })
      .catch((error: unknown) => {
        if (refreshGeneration === sessionGeneration) {
          clearSession()
        }

        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}
