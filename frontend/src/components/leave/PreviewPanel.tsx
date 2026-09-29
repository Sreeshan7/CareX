import { CalendarCheck2, Loader2, TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { PreviewResponse } from '../../api/types'
import { fmtDate } from '../../lib/dates'
import { formatDays } from '../../lib/utils'
import { Alert } from '../ui/primitives'

/** Server-computed working days / balance-after / conflict preview. The UI never computes working days itself. */
export function PreviewPanel({ preview, loading, compact }: { preview?: PreviewResponse; loading?: boolean; compact?: boolean }) {
  const { t } = useTranslation()
  if (!preview) {
    return loading ? <p className="flex items-center gap-2 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" />{t('common.loading')}</p>
      : <p className="text-sm text-slate-500">{t('apply.subtitle')}</p>
  }
  return (
    <div className="space-y-4" aria-live="polite">
      <div className="flex items-center gap-4">
        <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-slate-900 text-white shadow-sm ring-1 ring-slate-800"><CalendarCheck2 className="h-5 w-5" aria-hidden /></span>
        <div>
          <p className="text-[20px] font-bold tracking-tight text-slate-900">{t('apply.workingDays', { count: preview.workingDays })}</p>
          {preview.availableBefore !== null && (
            <p className="mt-0.5 text-[13px] font-medium text-slate-500">
              {t('apply.availableNow')}: <b className="text-slate-900">{formatDays(preview.availableBefore)}</b> → {t('apply.balanceAfter')}:{' '}
              <b className={preview.availableAfter !== null && preview.availableAfter < 0 ? 'text-rose-600' : 'text-slate-900'}>{formatDays(preview.availableAfter)}</b>
            </p>
          )}
        </div>
      </div>
      {!compact && preview.excludedDates.length > 0 && (
        <div className="pt-2">
          <p className="mb-2 text-[11px] font-bold uppercase tracking-widest text-slate-400">{t('apply.excluded')}</p>
          <ul className="flex flex-wrap gap-2">
            {preview.excludedDates.map((d) => (
              <li key={d.date} className="rounded-lg bg-white px-2.5 py-1 text-[12px] font-semibold text-slate-600 ring-1 ring-slate-200/80 shadow-sm">
                {fmtDate(d.date)} <span className="font-normal text-slate-400">· {d.reason === 'HOLIDAY' ? d.holidayName ?? t('apply.holiday') : t('apply.weekend')}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {preview.conflict?.wouldFlag && (
        <Alert tone="warn" icon={<TriangleAlert className="h-4 w-4" />}>
          {t('apply.conflictWarn', { date: fmtDate(preview.conflict.peakDate), absent: preview.conflict.peakAbsent, size: preview.conflict.teamSize })}
        </Alert>
      )}
      {preview.errors.length > 0 && (
        <Alert tone="error">
          <ul className="list-inside list-disc space-y-0.5">
            {preview.errors.map((e) => <li key={e.code}>{t(`errors.${e.code}`, e.message)}</li>)}
          </ul>
        </Alert>
      )}
    </div>
  )
}
