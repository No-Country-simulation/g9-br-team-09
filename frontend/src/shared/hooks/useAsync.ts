import {
  type DependencyList,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'

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

  const latestRequestId = useRef(0)
  const isMountedRef = useRef(true)

  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  const execute = useCallback(async () => {
    const requestId = ++latestRequestId.current
    const isStale = () =>
      requestId !== latestRequestId.current || !isMountedRef.current

    setIsLoading(true)
    setError(null)

    try {
      const result = await asyncFn()
      if (isStale()) return
      setData(result)
    } catch {
      if (isStale()) return
      setError(options.errorMessage ?? 'Não foi possível carregar os dados.')
    } finally {
      if (!isStale()) setIsLoading(false)
    }
    // eslint-disable-next-line react-hooks/use-memo, react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    execute()
  }, [execute])

  return { data, isLoading, error, refetch: execute }
}
