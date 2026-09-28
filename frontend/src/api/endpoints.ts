import { api, type Channel } from './client'
import type {
  AssistantReply, AssistantStatus, AuditView, BalanceView, CanonicalCommand, DemoAccount, EscalationView, HolidayView,
  HrOverview, LeaveRequestDetail, LeaveRequestSummary, LeaveTypeView, LoginResponse, ManagerDashboard,
  NotificationView, PageView, PreviewResponse, TeamCalendar, UserView,
} from './types'

const qs = (p: Record<string, unknown>) => {
  const s = new URLSearchParams()
  Object.entries(p).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') s.set(k, String(v)) })
  const str = s.toString()
  return str ? `?${str}` : ''
}

export const authApi = {
  login: (email: string, password: string) => api<LoginResponse>('/auth/login', { body: { email, password } }),
  demoLogin: (userKey: string) => api<LoginResponse>('/auth/demo-login', { body: { userKey } }),
  demoAccounts: () => api<{ enabled: boolean; accounts: DemoAccount[] }>('/auth/demo-accounts'),
  me: () => api<UserView>('/auth/me'),
}

export interface SubmitBody { leaveTypeCode: string; startDate: string; endDate: string; reason?: string; clientRequestId: string }

export const leaveApi = {
  types: () => api<LeaveTypeView[]>('/leave-types'),
  holidays: (year: number) => api<HolidayView[]>(`/holidays?year=${year}`),
  balances: (year?: number) => api<BalanceView[]>(`/me/balances${qs({ year })}`),
  mine: (status?: string, page = 0, size = 20) => api<PageView<LeaveRequestSummary>>(`/me/leave-requests${qs({ status, page, size })}`),
  preview: (b: { leaveTypeCode?: string; startDate?: string; endDate?: string }, signal?: AbortSignal) =>
    api<PreviewResponse>('/leave-requests/preview', { body: b, signal }),
  submit: (b: SubmitBody, channel: Channel = 'WEB') => api<LeaveRequestDetail>('/leave-requests', { body: b, channel }),
  detail: (id: number) => api<LeaveRequestDetail>(`/leave-requests/${id}`),
  decide: (id: number, b: { stage: 'MANAGER' | 'HR'; decision: 'APPROVE' | 'REJECT'; comment?: string; acknowledgeConflict?: boolean }, channel: Channel = 'WEB') =>
    api<LeaveRequestDetail>(`/leave-requests/${id}/decisions`, { body: b, channel }),
  cancel: (id: number, reason?: string, channel: Channel = 'WEB') =>
    api<LeaveRequestDetail>(`/leave-requests/${id}/cancel`, { body: { reason: reason || null }, channel }),
}

export const managerApi = {
  dashboard: () => api<ManagerDashboard>('/manager/dashboard'),
  approvals: (scope: 'pending' | 'decided' = 'pending') => api<LeaveRequestSummary[]>(`/manager/approvals?scope=${scope}`),
  team: (from?: string, to?: string) => api<TeamCalendar>(`/manager/team${qs({ from, to })}`),
  conflicts: () => api<LeaveRequestSummary[]>('/manager/conflicts'),
  escalations: () => api<EscalationView[]>('/manager/escalations'),
}

export const hrApi = {
  approvals: () => api<LeaveRequestSummary[]>('/hr/approvals'),
  escalations: (open = false) => api<EscalationView[]>(`/hr/escalations?open=${open}`),
  runEscalations: () => api<{ escalated: number }>('/hr/escalations/run', { method: 'POST' }),
  search: (p: { status?: string; teamId?: number; from?: string; to?: string; flagged?: boolean; page?: number; size?: number }) =>
    api<PageView<LeaveRequestSummary>>(`/hr/leave-requests${qs(p)}`),
  overview: (year?: number) => api<HrOverview>(`/hr/overview${qs({ year })}`),
  audit: (p: { entityType?: string; entityId?: number; action?: string; from?: string; to?: string; page?: number; size?: number }) =>
    api<PageView<AuditView>>(`/hr/audit${qs(p)}`),
  integrity: () => api<{ ok: boolean; mismatches: unknown[] }>('/hr/balance-integrity'),
  teams: () => api<{ id: number; name: string }[]>('/hr/teams'),
  calendar: (teamId?: number, from?: string, to?: string) => api<TeamCalendar>(`/hr/team-calendar${qs({ teamId, from, to })}`),
  conflicts: () => api<LeaveRequestSummary[]>('/hr/conflicts'),
  demoReset: () => api<{ ok: boolean }>('/hr/demo/reset', { method: 'POST' }),
}

export const notificationApi = {
  list: (unreadOnly = false) => api<NotificationView[]>(`/notifications?unreadOnly=${unreadOnly}&limit=30`),
  count: () => api<{ count: number }>('/notifications/unread-count'),
  read: (id: number) => api(`/notifications/${id}/read`, { method: 'POST' }),
  readAll: () => api('/notifications/read-all', { method: 'POST' }),
}

export const assistantApi = {
  status: () => api<AssistantStatus>('/assistant/status'),
  message: (text: string, language: string, draftCommand: CanonicalCommand | null, channel: Channel) =>
    api<AssistantReply>('/assistant/message', { body: { text, language, draftCommand }, channel }),
  transcribe: (audio: Blob, languageHint: string) => {
    const form = new FormData()
    form.append('audio', audio, audio.type.includes('wav') ? 'speech.wav' : 'speech.webm')
    form.append('languageHint', languageHint)
    return api<{ transcript: string; languageCode: string | null; confidence: number | null }>('/assistant/transcribe', { form, channel: 'VOICE' })
  },
  speak: async (text: string, languageCode: string): Promise<Blob> => {
    const res = await api<Response>('/assistant/speak', { body: { text, languageCode }, raw: true })
    return res.blob()
  },
}
