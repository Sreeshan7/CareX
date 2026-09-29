import { AlertTriangle, Ban, CheckCircle2, Clock, ShieldCheck, XCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { LeaveStatus } from '../../api/types'
import { STATUS_META } from '../../lib/status'
import { Badge } from '../ui/primitives'
import { cn } from '../../lib/utils'
import { humanMinutes, minutesUntil } from '../../lib/dates'
import { useNow } from '../../hooks/useNow'

const ICONS = {
  clock: Clock, alert: AlertTriangle, check: CheckCircle2, x: XCircle, ban: Ban, shield: ShieldCheck,
}

export function StatusBadge({ status, className }: { status: LeaveStatus; className?: string }) {
  const { t } = useTranslation()
  const meta = STATUS_META[status]
  const Icon = ICONS[meta.icon]
  return (
    <Badge className={cn('px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider', meta.tone, className)}>
      <Icon className="mr-1 h-3.5 w-3.5" aria-hidden />
      {t(`status.${status}`)}
    </Badge>
  )
}

export function FlagBadge() {
  const { t } = useTranslation()
  return (
    <Badge className="bg-amber-50 text-amber-700 ring-amber-200/50 px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider" title={t('common.flagged')}>
      <AlertTriangle className="mr-1 h-3.5 w-3.5" aria-hidden /> {t('common.flagged')}
    </Badge>
  )
}

/** Live countdown to the stage deadline; turns red when overdue. */
export function DeadlineBadge({ deadline }: { deadline: string | null }) {
  const { t } = useTranslation()
  useNow(30_000)
  const mins = minutesUntil(deadline)
  if (mins === null) return null
  const overdue = mins < 0
  return (
    <Badge className={cn('px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider', overdue ? 'bg-rose-50 text-rose-700 ring-rose-200/50' : 'bg-slate-100 text-slate-600 ring-slate-200/50')}>
      <Clock className="mr-1 h-3.5 w-3.5" aria-hidden />
      {overdue ? t('common.overdueBy', { time: humanMinutes(mins) }) : t('common.dueIn', { time: humanMinutes(mins) })}
    </Badge>
  )
}

export function LeaveTypeDot({ code }: { code: string }) {
  const { t } = useTranslation()
  const colors: Record<string, string> = { ANNUAL: 'bg-[#3b82f6]', CASUAL: 'bg-[#8b5cf6]', SICK: 'bg-[#f43f5e]' }
  return (
    <span className="inline-flex items-center gap-2 text-[13px] font-semibold text-slate-700">
      <span className={cn('h-2.5 w-2.5 rounded-full ring-2 ring-white shadow-sm', colors[code] ?? 'bg-slate-400')} aria-hidden />
      {t(`leaveType.${code}`, code)}
    </span>
  )
}
