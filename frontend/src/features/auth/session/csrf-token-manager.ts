let csrfToken: string | null = null

export function getCsrfToken(): string | null {
  return csrfToken
}

export function setCsrfToken(token: string): void {
  csrfToken = token
}

export function clearCsrfToken(): void {
  csrfToken = null
}
