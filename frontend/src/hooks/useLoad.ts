import { useCallback, useEffect, useState, type DependencyList } from 'react'
import { errorMessage } from '../api/client'

/** Minimal data-loading hook: loads on mount / when deps change, exposes reload and local updates. */
export function useLoad<T>(loader: () => Promise<T>, deps: DependencyList = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(loader, deps)

  const reload = useCallback(() => {
    setLoading(true)
    return load()
      .then((value) => {
        setData(value)
        setError(null)
      })
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
  }, [load])

  useEffect(() => {
    void reload()
  }, [reload])

  return { data, setData, error, loading, reload }
}
