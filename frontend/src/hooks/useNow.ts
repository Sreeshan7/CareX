import * as React from 'react'

/** Re-renders the component every {@code intervalMs} (for countdowns). */
export function useNow(intervalMs = 30_000): number {
  const [now, setNow] = React.useState(() => Date.now())
  React.useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(id)
  }, [intervalMs])
  return now
}

export function useDebounced<T>(value: T, ms = 400): T {
  const [v, setV] = React.useState(value)
  React.useEffect(() => {
    const id = window.setTimeout(() => setV(value), ms)
    return () => window.clearTimeout(id)
  }, [value, ms])
  return v
}
