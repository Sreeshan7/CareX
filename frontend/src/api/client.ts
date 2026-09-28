// Fetch wrapper: base URL, Bearer token, X-Request-Id, X-Client-Channel, ProblemDetail → ApiError.

export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: { field: string; message: string }[]
  correlationId: string | null
  currentStatus: string | null
  extra: Record<string, unknown>

  constructor(status: number, code: string, message: string, body: Record<string, any> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = body.errors ?? []
    this.correlationId = body.correlationId ?? null
    this.currentStatus = body.currentStatus ?? null
    this.extra = body
  }
}

const TOKEN_KEY = 'carex.token'
let memoryToken: string | null = null
let onUnauthorized: (() => void) | null = null

export function setToken(token: string | null) {
  memoryToken = token
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token)
    else sessionStorage.removeItem(TOKEN_KEY)
  } catch { /* storage unavailable */ }
}

export function getToken(): string | null {
  if (memoryToken) return memoryToken
  try { memoryToken = sessionStorage.getItem(TOKEN_KEY) } catch { memoryToken = null }
  return memoryToken
}

export function setUnauthorizedHandler(fn: () => void) { onUnauthorized = fn }

function requestId() {
  return Math.random().toString(36).slice(2, 14)
}

export type Channel = 'WEB' | 'CHAT' | 'VOICE'

export interface RequestOptions {
  method?: string
  body?: unknown
  form?: FormData
  channel?: Channel
  signal?: AbortSignal
  raw?: boolean
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { 'X-Request-Id': requestId(), Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (opts.channel) headers['X-Client-Channel'] = opts.channel
  let body: BodyInit | undefined
  if (opts.form) body = opts.form
  else if (opts.body !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opts.body) }

  let res: Response
  try {
    res = await fetch(`/api/v1${path}`, { method: opts.method ?? (body ? 'POST' : 'GET'), headers, body, signal: opts.signal })
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK_ERROR', 'Network error — check your connection')
  }
  if (res.status === 401 && token) onUnauthorized?.()
  if (!res.ok) {
    let data: Record<string, any> = {}
    try { data = await res.json() } catch { /* not json */ }
    throw new ApiError(res.status, data.code ?? `HTTP_${res.status}`, data.detail ?? res.statusText, data)
  }
  if (opts.raw) return res as unknown as T
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}
