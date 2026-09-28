import type { LeaveStatus } from '../api/types'

export interface StatusMeta { tone: string; dot: string; icon: 'clock' | 'alert' | 'check' | 'x' | 'ban' | 'shield' }

// Every status is conveyed by colour + icon + text (never colour alone).
export const STATUS_META: Record<LeaveStatus, StatusMeta> = {
  PENDING_MANAGER: { tone: 'bg-amber-50 text-amber-800 ring-amber-200', dot: 'bg-amber-500', icon: 'clock' },
  MANAGER_ESCALATED: { tone: 'bg-red-50 text-red-700 ring-red-200', dot: 'bg-red-500', icon: 'alert' },
  PENDING_HR: { tone: 'bg-sky-50 text-sky-800 ring-sky-200', dot: 'bg-sky-500', icon: 'shield' },
  HR_ESCALATED: { tone: 'bg-red-50 text-red-700 ring-red-200', dot: 'bg-red-500', icon: 'alert' },
  APPROVED: { tone: 'bg-emerald-50 text-emerald-800 ring-emerald-200', dot: 'bg-emerald-500', icon: 'check' },
  REJECTED: { tone: 'bg-rose-50 text-rose-700 ring-rose-200', dot: 'bg-rose-500', icon: 'x' },
  CANCELLED: { tone: 'bg-slate-100 text-slate-600 ring-slate-200', dot: 'bg-slate-400', icon: 'ban' },
}

export const TYPE_COLORS: Record<string, string> = {
  ANNUAL: 'bg-brand-600',
  CASUAL: 'bg-violet-500',
  SICK: 'bg-orange-500',
}

export function isPending(s: LeaveStatus) {
  return s === 'PENDING_MANAGER' || s === 'MANAGER_ESCALATED' || s === 'PENDING_HR' || s === 'HR_ESCALATED'
}
