import { Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { BalanceView } from '../../api/types'
import { Card } from '../ui/primitives'
import { formatDays } from '../../lib/utils'

const RING: Record<string, string> = { ANNUAL: '#0d9488', CASUAL: '#8b5cf6', SICK: '#f97316' }

/** Ring chart: used (solid) / pending (light) / available (track). Pro-ration basis shown as a tooltip + text. */
export function BalanceCard({ b }: { b: BalanceView }) {
  const { t } = useTranslation()
  const total = Math.max(Number(b.entitled) + Number(b.adjustment), 0.0001)
  const used = Number(b.used) / total
  const pending = Number(b.pending) / total
  const r = 34
  const c = 2 * Math.PI * r
  const color = RING[b.leaveTypeCode] ?? '#64748b'
  const prorated = !b.prorationBasis.startsWith('Joined before') && !b.prorationBasis.startsWith('Not pro-rated')
  return (
    <Card className="p-5">
      <div className="flex items-center gap-4">
        <svg viewBox="0 0 80 80" className="h-20 w-20 shrink-0 -rotate-90" role="img" aria-label={`${b.leaveTypeName}: ${formatDays(b.available)} ${t('employee.available')}`}>
          <circle cx="40" cy="40" r={r} fill="none" stroke="#e2e8f0" strokeWidth="9" />
          <circle cx="40" cy="40" r={r} fill="none" stroke={color} strokeOpacity="0.35" strokeWidth="9"
            strokeDasharray={`${(used + pending) * c} ${c}`} strokeLinecap="round" />
          <circle cx="40" cy="40" r={r} fill="none" stroke={color} strokeWidth="9" strokeDasharray={`${used * c} ${c}`} strokeLinecap="round" />
        </svg>
        <div className="min-w-0">
          <p className="text-sm font-medium text-slate-500">{t(`leaveType.${b.leaveTypeCode}`, b.leaveTypeName)}</p>
          <p className="text-3xl font-semibold tracking-tight text-slate-900">
            {formatDays(b.available)}<span className="ml-1 text-sm font-normal text-slate-500">{t('employee.available')}</span>
          </p>
          <p className="mt-0.5 text-xs text-slate-500">
            {formatDays(b.used)} {t('employee.used')} · {formatDays(b.pending)} {t('employee.pending')} · {formatDays(b.entitled)} {t('employee.entitled')}
          </p>
        </div>
      </div>
      {prorated && (
        <p className="mt-3 flex items-start gap-1.5 rounded-lg bg-slate-50 px-2.5 py-2 text-xs text-slate-600" title={b.prorationBasis}>
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-brand-600" aria-hidden />
          <span><span className="font-medium">{t('employee.prorated')}:</span> {b.prorationBasis}</span>
        </p>
      )}
    </Card>
  )
}
