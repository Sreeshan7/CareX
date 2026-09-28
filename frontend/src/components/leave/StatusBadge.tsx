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
    <Badge className={cn(meta.tone, className)}>
      <Icon className="h-3.5 w-3.5" aria-hidden />
      {t(`status.${status}`)}
    </Badge>
  )
}

export function FlagBadge() {
  const { t } = useTranslation()
  return (
    <Badge className="bg-orange-50 text-orange-800 ring-orange-200" title={t('common.flagged')}>
      <AlertTriangle className="h-3.5 w-3.5" aria-hidden /> {t('common.flagged')}
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
    <Badge className={overdue ? 'bg-red-50 text-red-700 ring-red-200' : 'bg-slate-50 text-slate-600 ring-slate-200'}>
      <Clock className="h-3.5 w-3.5" aria-hidden />
      {overdue ? t('common.overdueBy', { time: humanMinutes(mins) }) : t('common.dueIn', { time: humanMinutes(mins) })}
    </Badge>
  )
}

export function LeaveTypeDot({ code }: { code: string }) {
  const { t } = useTranslation()
  const colors: Record<string, string> = { ANNUAL: 'bg-brand-600', CASUAL: 'bg-violet-500', SICK: 'bg-orange-500' }
  return (
    <span className="inline-flex items-center gap-1.5 text-sm text-slate-700">
      <span className={cn('h-2 w-2 rounded-full', colors[code] ?? 'bg-slate-400')} aria-hidden />
      {t(`leaveType.${code}`, code)}
    </span>
  )
}
