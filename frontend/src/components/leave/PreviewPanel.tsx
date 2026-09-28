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
    <div className="space-y-3" aria-live="polite">
      <div className="flex items-center gap-3">
        <span className="rounded-xl bg-brand-50 p-2.5 text-brand-700"><CalendarCheck2 className="h-5 w-5" aria-hidden /></span>
        <div>
          <p className="text-lg font-semibold text-slate-900">{t('apply.workingDays', { count: preview.workingDays })}</p>
          {preview.availableBefore !== null && (
            <p className="text-sm text-slate-600">
              {t('apply.availableNow')}: <b>{formatDays(preview.availableBefore)}</b> → {t('apply.balanceAfter')}:{' '}
              <b className={preview.availableAfter !== null && preview.availableAfter < 0 ? 'text-rose-600' : 'text-slate-900'}>{formatDays(preview.availableAfter)}</b>
            </p>
          )}
        </div>
      </div>
      {!compact && preview.excludedDates.length > 0 && (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{t('apply.excluded')}</p>
          <ul className="mt-1 flex flex-wrap gap-1.5">
            {preview.excludedDates.map((d) => (
              <li key={d.date} className="rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                {fmtDate(d.date)} · {d.reason === 'HOLIDAY' ? d.holidayName ?? t('apply.holiday') : t('apply.weekend')}
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
