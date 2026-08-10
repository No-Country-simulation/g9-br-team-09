import { refresh } from '../api/auth-api'
import type { AuthenticationResponse } from '../schemas/responses'
import { clearCsrfToken } from './csrf-token-manager'
import { clearAccessToken, setAccessToken } from './token-manager'

type SessionInvalidHandler = () => void

let refreshPromise: Promise<AuthenticationResponse> | null = null
let sessionInvalidHandler: SessionInvalidHandler | null = null

export function setSessionInvalidHandler(
  handler: SessionInvalidHandler | null,
): void {
  sessionInvalidHandler = handler
}

export function clearSession(): void {
  clearAccessToken()
  clearCsrfToken()
  sessionInvalidHandler?.()
}

export function refreshAccessToken(): Promise<AuthenticationResponse> {
  if (!refreshPromise) {
    refreshPromise = refresh()
      .then((authentication) => {
        setAccessToken(authentication.access_token)
        return authentication
      })
      .catch((error: unknown) => {
        clearSession()
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}
