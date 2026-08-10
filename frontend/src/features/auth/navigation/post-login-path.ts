export function getPostLoginPath(state: unknown): string {
  if (!state || typeof state !== 'object' || !('from' in state)) {
    return '/analise-energetica'
  }

  const from = state.from
  if (!from || typeof from !== 'object' || !('pathname' in from)) {
    return '/analise-energetica'
  }

  const {
    pathname,
    search = '',
    hash = '',
  } = from as {
    pathname?: unknown
    search?: unknown
    hash?: unknown
  }

  if (
    typeof pathname !== 'string' ||
    !pathname.startsWith('/') ||
    pathname.startsWith('//')
  ) {
    return '/analise-energetica'
  }

  return `${pathname}${typeof search === 'string' ? search : ''}${typeof hash === 'string' ? hash : ''}`
}
