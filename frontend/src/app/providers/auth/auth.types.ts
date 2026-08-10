import type { LoginFormValues } from '@/features/auth/schemas/login'
import type { RegisterFormValues } from '@/features/auth/schemas/register'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthUser {
  id: number
  nome: string
  email: string
  role: 'USER'
}

export interface AuthContextValue {
  status: AuthStatus
  user: AuthUser | null
  login: (input: LoginFormValues) => Promise<void>
  register: (input: RegisterFormValues) => Promise<void>
  logout: () => Promise<void>
  restoreSession: () => Promise<void>
}
