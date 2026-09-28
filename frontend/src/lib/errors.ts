import { ApiError } from '../api/client'
import i18n from '../i18n'

/** Maps backend error codes to localized, human messages; unknown codes fall back to the server detail + ref. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    const key = `errors.${e.code}`
    if (i18n.exists(key)) {
      const base = i18n.t(key)
      return e.code === 'INTERNAL_ERROR' && e.correlationId ? `${base} (Ref: ${e.correlationId})` : base
    }
    const ref = e.correlationId ? ` (Ref: ${e.correlationId})` : ''
    return `${e.message || i18n.t('errors.GENERIC')}${ref}`
  }
  if (e instanceof Error) return e.message
  return i18n.t('errors.GENERIC')
}
