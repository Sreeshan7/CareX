import { differenceInMinutes, formatDistanceToNowStrict, parseISO } from 'date-fns'
import i18n from '../i18n'

const localeFor = () => {
  const l = i18n.language || 'en'
  return l.startsWith('hi') ? 'hi-IN' : l.startsWith('ta') ? 'ta-IN' : 'en-IN'
}

/** "Mon, 5 Oct" */
export function fmtDate(iso: string | null | undefined, opts: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short' }) {
  if (!iso) return '—'
  const d = iso.length === 10 ? new Date(`${iso}T00:00:00`) : parseISO(iso)
  return new Intl.DateTimeFormat(localeFor(), opts).format(d)
}

export function fmtRange(start: string, end: string) {
  if (start === end) return fmtDate(start)
  return `${fmtDate(start)} → ${fmtDate(end)}`
}

export function fmtDateTime(iso: string | null | undefined) {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(localeFor(), { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(parseISO(iso))
}

export function ago(iso: string | null | undefined) {
  if (!iso) return ''
  return formatDistanceToNowStrict(parseISO(iso), { addSuffix: true })
}

/** Positive = minutes remaining, negative = overdue minutes. */
export function minutesUntil(iso: string | null | undefined): number | null {
  if (!iso) return null
  return differenceInMinutes(parseISO(iso), new Date())
}

export function humanMinutes(mins: number): string {
  const m = Math.abs(mins)
  if (m < 60) return `${m}m`
  if (m < 60 * 48) return `${Math.floor(m / 60)}h ${m % 60}m`
  return `${Math.floor(m / 1440)}d`
}

export function isoToday(): string {
  const d = new Date()
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

export function addDaysIso(iso: string, n: number): string {
  const d = new Date(`${iso}T00:00:00`)
  d.setDate(d.getDate() + n)
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}
