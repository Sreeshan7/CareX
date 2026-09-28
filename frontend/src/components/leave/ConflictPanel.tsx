import { TriangleAlert, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { ConflictView } from '../../api/types'
import { fmtDate } from '../../lib/dates'
import { cn } from '../../lib/utils'
import { Alert } from '../ui/primitives'

/** Team absence heat strip: one cell per working day, red above threshold. Names only for manager/HR. */
export function ConflictPanel({ conflict }: { conflict: ConflictView }) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      {conflict.flagged ? (
        <Alert tone="warn" icon={<TriangleAlert className="h-4 w-4" />}>
          <p className="font-medium">{t('detail.conflictFlagged', { pct: conflict.thresholdPct })}</p>
          {conflict.peakDate && (
            <p className="mt-0.5">{t('detail.peak', { date: fmtDate(conflict.peakDate), absent: conflict.peakAbsent, size: conflict.teamSize })}</p>
          )}
          {conflict.acknowledgedByManager && <p className="mt-0.5 text-xs">{t('detail.ackBy', { name: conflict.acknowledgedByManager })} (manager)</p>}
          {conflict.acknowledgedByHr && <p className="mt-0.5 text-xs">{t('detail.ackBy', { name: conflict.acknowledgedByHr })} (HR)</p>}
        </Alert>
      ) : (
        <p className="flex items-center gap-2 text-sm text-slate-600"><Users className="h-4 w-4 text-emerald-600" aria-hidden />{t('detail.noConflict')}</p>
      )}
      <div className="flex flex-wrap gap-1.5">
        {conflict.days.map((d) => (
          <div
            key={d.date}
            className={cn('min-w-[64px] rounded-lg border px-2 py-1.5 text-center',
              d.over ? 'border-red-200 bg-red-50' : d.absent > 1 ? 'border-amber-200 bg-amber-50' : 'border-slate-200 bg-white')}
            title={d.people ? d.people.join(', ') : undefined}
          >
            <p className="text-[11px] font-medium text-slate-500">{fmtDate(d.date, { day: 'numeric', month: 'short' })}</p>
            <p className={cn('text-sm font-semibold', d.over ? 'text-red-700' : 'text-slate-800')}>{d.absent}/{d.teamSize}</p>
          </div>
        ))}
      </div>
      {conflict.namesVisible && conflict.days.some((d) => d.people && d.people.length > 1) && (
        <ul className="space-y-1 text-xs text-slate-600">
          {conflict.days.filter((d) => d.people && d.people.length > 0).map((d) => (
            <li key={d.date}><span className="font-medium">{fmtDate(d.date)}:</span> {d.people!.join(', ')}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
