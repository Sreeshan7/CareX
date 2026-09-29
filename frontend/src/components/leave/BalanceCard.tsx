import { Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { BalanceView } from '../../api/types'
import { Card } from '../ui/primitives'
import { formatDays } from '../../lib/utils'

const RING: Record<string, string> = { ANNUAL: '#3b82f6', CASUAL: '#8b5cf6', SICK: '#f43f5e' }

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
    <Card className="p-6 transition-all duration-300 hover:shadow-md hover:ring-slate-300/60">
      <div className="flex items-center gap-5">
        <div className="relative flex h-[88px] w-[88px] shrink-0 items-center justify-center">
          <svg viewBox="0 0 80 80" className="absolute inset-0 h-full w-full -rotate-90 transform drop-shadow-sm transition-transform duration-500 ease-out hover:scale-105" role="img" aria-label={`${b.leaveTypeName}: ${formatDays(b.available)} ${t('employee.available')}`}>
            <circle cx="40" cy="40" r={r} fill="none" stroke="#f1f5f9" strokeWidth="7" />
            <circle cx="40" cy="40" r={r} fill="none" stroke={color} strokeOpacity="0.2" strokeWidth="7"
              strokeDasharray={`${(used + pending) * c} ${c}`} strokeLinecap="round" className="transition-all duration-1000 ease-in-out" />
            <circle cx="40" cy="40" r={r} fill="none" stroke={color} strokeWidth="7" strokeDasharray={`${used * c} ${c}`} strokeLinecap="round" className="transition-all duration-1000 ease-in-out" />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
            <span className="text-xl font-bold tracking-tight text-slate-900">{formatDays(b.available)}</span>
          </div>
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[14px] font-semibold tracking-tight text-slate-900">{t(`leaveType.${b.leaveTypeCode}`, b.leaveTypeName)}</p>
          <div className="mt-2.5 flex items-center gap-3 text-[12px] font-medium text-slate-500">
            <div className="flex items-center gap-1.5"><div className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: color }}></div> {formatDays(b.used)} {t('employee.used')}</div>
            <div className="flex items-center gap-1.5"><div className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: color, opacity: 0.3 }}></div> {formatDays(b.pending)} {t('employee.pending')}</div>
          </div>
          <p className="mt-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
            {formatDays(b.entitled)} {t('employee.entitled')} total
          </p>
        </div>
      </div>
      {prorated && (
        <p className="mt-5 flex items-start gap-2.5 rounded-xl bg-slate-50 px-3.5 py-3 text-[12px] font-medium text-slate-600 ring-1 ring-inset ring-slate-200/50" title={b.prorationBasis}>
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-accent-500" aria-hidden />
          <span className="leading-relaxed"><span className="font-bold text-slate-900">{t('employee.prorated')}:</span> {b.prorationBasis}</span>
        </p>
      )}
    </Card>
  )
}
