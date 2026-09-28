import { Link } from 'react-router-dom'
import { ChevronRight, MessageSquare, Mic } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { LeaveRequestSummary } from '../../api/types'
import { ago, fmtRange } from '../../lib/dates'
import { cn, formatDays } from '../../lib/utils'
import { DeadlineBadge, FlagBadge, LeaveTypeDot, StatusBadge } from './StatusBadge'
import { RequestActions } from './RequestActions'

/** Table on desktop, cards on mobile. Escalated rows get a red left border. */
export function RequestTable({ rows, showEmployee = true, showActions = false, showDeadline = false }: {
  rows: LeaveRequestSummary[]; showEmployee?: boolean; showActions?: boolean; showDeadline?: boolean
}) {
  const { t } = useTranslation()
  return (
    <>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-5 py-3 font-medium">#</th>
              {showEmployee && <th className="px-3 py-3 font-medium">{t('common.employee')}</th>}
              <th className="px-3 py-3 font-medium">{t('common.type')}</th>
              <th className="px-3 py-3 font-medium">{t('common.dates')}</th>
              <th className="px-3 py-3 font-medium">{t('common.status')}</th>
              <th className="px-3 py-3 font-medium">{t('common.requested')}</th>
              <th className="px-5 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((r) => (
              <tr key={r.id} className={cn('hover:bg-slate-50/70', r.escalated && 'shadow-[inset_3px_0_0_#ef4444]')}>
                <td className="px-5 py-3 font-medium text-slate-500">
                  <Link to={`/requests/${r.id}`} className="hover:text-brand-700 hover:underline">#{r.id}</Link>
                </td>
                {showEmployee && (
                  <td className="px-3 py-3">
                    <p className="font-medium text-slate-900">{r.employee.name}</p>
                    <p className="text-xs text-slate-500">{r.employee.teamName}</p>
                  </td>
                )}
                <td className="px-3 py-3"><LeaveTypeDot code={r.leaveTypeCode} /></td>
                <td className="px-3 py-3">
                  <p className="text-slate-900">{fmtRange(r.startDate, r.endDate)}</p>
                  <p className="text-xs text-slate-500">{t('common.days', { count: Number(formatDays(r.workingDays)) })}</p>
                </td>
                <td className="px-3 py-3">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <StatusBadge status={r.status} />
                    {r.flagged && <FlagBadge />}
                    {showDeadline && !r.escalated && <DeadlineBadge deadline={r.stageDeadlineAt} />}
                    {r.channel === 'VOICE' && <Mic className="h-3.5 w-3.5 text-brand-600" aria-label={t('common.voice')} />}
                    {r.channel === 'CHAT' && <MessageSquare className="h-3.5 w-3.5 text-brand-600" aria-label={t('common.chat')} />}
                  </div>
                </td>
                <td className="px-3 py-3 text-xs text-slate-500">{ago(r.createdAt)}</td>
                <td className="px-5 py-3">
                  <div className="flex items-center justify-end gap-2">
                    {showActions && (
                      <RequestActions size="sm" allowed={r.allowedActions.filter((a) => a !== 'CANCEL')}
                        target={{ id: r.id, employeeName: r.employee.name, flagged: r.flagged, range: fmtRange(r.startDate, r.endDate) }} />
                    )}
                    <Link to={`/requests/${r.id}`} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label={`${t('common.view')} #${r.id}`}>
                      <ChevronRight className="h-4 w-4" />
                    </Link>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="divide-y divide-slate-100 md:hidden">
        {rows.map((r) => (
          <li key={r.id} className={cn('p-4', r.escalated && 'shadow-[inset_3px_0_0_#ef4444]')}>
            <Link to={`/requests/${r.id}`} className="block">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-sm font-medium text-slate-900">{showEmployee ? r.employee.name : `#${r.id}`}</p>
                  <p className="text-sm text-slate-600">{fmtRange(r.startDate, r.endDate)} · {t('common.days', { count: Number(formatDays(r.workingDays)) })}</p>
                </div>
                <LeaveTypeDot code={r.leaveTypeCode} />
              </div>
              <div className="mt-2 flex flex-wrap gap-1.5">
                <StatusBadge status={r.status} />
                {r.flagged && <FlagBadge />}
              </div>
            </Link>
            {showActions && (
              <div className="mt-3">
                <RequestActions size="sm" allowed={r.allowedActions.filter((a) => a !== 'CANCEL')}
                  target={{ id: r.id, employeeName: r.employee.name, flagged: r.flagged, range: fmtRange(r.startDate, r.endDate) }} />
              </div>
            )}
          </li>
        ))}
      </ul>
    </>
  )
}
