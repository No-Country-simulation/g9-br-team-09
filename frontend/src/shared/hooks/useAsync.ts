import { type DependencyList, useCallback, useEffect, useState } from 'react'

interface UseAsyncOptions {
  errorMessage?: string
}

export function useAsync<T>(
  asyncFn: () => Promise<T>,
  deps: DependencyList = [],
  options: UseAsyncOptions = {},
) {
  const [data, setData] = useState<T | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const execute = useCallback(async () => {
    setIsLoading(true)
    setError(null)

    try {
      const result = await asyncFn()
      setData(result)
    } catch {
      setError(options.errorMessage ?? 'Não foi possível carregar os dados.')
    } finally {
      setIsLoading(false)
    }
    // eslint-disable-next-line react-hooks/use-memo, react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    // fetch inicial: isLoading/error já nascem no valor correto (true/null)
    execute()
  }, [execute])

  return { data, isLoading, error, refetch: execute }
}
