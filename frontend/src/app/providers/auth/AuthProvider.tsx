import type { PropsWithChildren } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import {
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
} from '@/features/auth/api/auth-api'
import {
  clearSession,
  refreshAccessToken,
  setSessionInvalidHandler,
} from '@/features/auth/session/session-manager'
import { setAccessToken } from '@/features/auth/session/token-manager'

import type { AuthContextValue, AuthStatus, AuthUser } from './auth.types'
import { AuthContext } from './AuthContext'

interface AuthState {
  status: AuthStatus
  user: AuthUser | null
}

const ANONYMOUS_STATE: AuthState = { status: 'anonymous', user: null }

export function AuthProvider({ children }: PropsWithChildren) {
  const [authState, setAuthState] = useState<AuthState>({
    status: 'loading',
    user: null,
  })
  const restoreGeneration = useRef(0)

  const markAnonymous = useCallback(() => {
    setAuthState(ANONYMOUS_STATE)
  }, [])

  const restoreSession = useCallback(async () => {
    const generation = ++restoreGeneration.current

    try {
      const authentication = await refreshAccessToken()

      if (generation !== restoreGeneration.current) {
        return
      }

      setAuthState({ status: 'authenticated', user: authentication.usuario })
    } catch {
      return
    }
  }, [])

  useEffect(() => {
    setSessionInvalidHandler(markAnonymous)
    void Promise.resolve().then(restoreSession)

    return () => {
      restoreGeneration.current += 1
      setSessionInvalidHandler(null)
    }
  }, [markAnonymous, restoreSession])

  const value = useMemo<AuthContextValue>(
    () => ({
      ...authState,
      login: async (input) => {
        const authentication = await loginRequest(input)
        setAccessToken(authentication.access_token)
        setAuthState({ status: 'authenticated', user: authentication.usuario })
      },
      register: async (input) => {
        await registerRequest(input)
      },
      logout: async () => {
        try {
          await logoutRequest()
        } finally {
          clearSession()
        }
      },
      restoreSession,
    }),
    [authState, restoreSession],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
