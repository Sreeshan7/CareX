import { Link } from 'react-router-dom'
import { ChevronRight, MessageSquare, Mic } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { LeaveRequestSummary } from '../../api/types'
import { ago, fmtRange } from '../../lib/dates'
import { cn, formatDays } from '../../lib/utils'
import { DeadlineBadge, FlagBadge, LeaveTypeDot, StatusBadge } from './StatusBadge'
import { RequestActions } from './RequestActions'

export function RequestTable({ rows, showEmployee = true, showActions = false, showDeadline = false }: {
  rows: LeaveRequestSummary[]; showEmployee?: boolean; showActions?: boolean; showDeadline?: boolean
}) {
  const { t } = useTranslation()
  return (
    <>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full text-left text-[13px]">
          <thead className="border-b border-slate-200/80 bg-slate-50/50 text-[11px] font-bold uppercase tracking-widest text-slate-500">
            <tr>
              <th className="px-6 py-4 font-bold">#</th>
              {showEmployee && <th className="px-4 py-4 font-bold">{t('common.employee')}</th>}
              <th className="px-4 py-4 font-bold">{t('common.type')}</th>
              <th className="px-4 py-4 font-bold">{t('common.dates')}</th>
              <th className="px-4 py-4 font-bold">{t('common.status')}</th>
              <th className="px-4 py-4 font-bold">{t('common.requested')}</th>
              <th className="px-6 py-4" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100/80">
            {rows.map((r) => (
              <tr key={r.id} className={cn('group transition-colors hover:bg-slate-50/70', r.escalated && 'shadow-[inset_3px_0_0_#ef4444]')}>
                <td className="px-6 py-4 font-semibold text-slate-500">
                  <Link to={`/requests/${r.id}`} className="hover:text-accent-600 transition-colors">#{r.id}</Link>
                </td>
                {showEmployee && (
                  <td className="px-4 py-4">
                    <p className="font-semibold text-slate-900">{r.employee.name}</p>
                    <p className="mt-0.5 text-[12px] font-medium text-slate-500">{r.employee.teamName}</p>
                  </td>
                )}
                <td className="px-4 py-4 font-medium"><LeaveTypeDot code={r.leaveTypeCode} /></td>
                <td className="px-4 py-4">
                  <p className="font-medium text-slate-900">{fmtRange(r.startDate, r.endDate)}</p>
                  <p className="mt-0.5 text-[12px] font-medium text-slate-500">{t('common.days', { count: Number(formatDays(r.workingDays)) })}</p>
                </td>
                <td className="px-4 py-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusBadge status={r.status} />
                    {r.flagged && <FlagBadge />}
                    {showDeadline && !r.escalated && <DeadlineBadge deadline={r.stageDeadlineAt} />}
                    {r.channel === 'VOICE' && <div className="flex h-6 w-6 items-center justify-center rounded-full bg-slate-100 text-slate-500 ring-1 ring-slate-200/60"><Mic className="h-3.5 w-3.5" aria-label={t('common.voice')} /></div>}
                    {r.channel === 'CHAT' && <div className="flex h-6 w-6 items-center justify-center rounded-full bg-slate-100 text-slate-500 ring-1 ring-slate-200/60"><MessageSquare className="h-3.5 w-3.5" aria-label={t('common.chat')} /></div>}
                  </div>
                </td>
                <td className="px-4 py-4 text-[12px] font-medium text-slate-500">{ago(r.createdAt)}</td>
                <td className="px-6 py-4">
                  <div className="flex items-center justify-end gap-3 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
                    {showActions && (
                      <RequestActions size="sm" allowed={r.allowedActions.filter((a) => a !== 'CANCEL')}
                        target={{ id: r.id, employeeName: r.employee.name, flagged: r.flagged, range: fmtRange(r.startDate, r.endDate) }} />
                    )}
                    <Link to={`/requests/${r.id}`} className="flex h-8 w-8 items-center justify-center rounded-lg bg-white ring-1 ring-slate-200/80 text-slate-400 hover:bg-slate-50 hover:text-slate-900 shadow-sm transition-all" aria-label={`${t('common.view')} #${r.id}`}>
                      <ChevronRight className="h-4 w-4" />
                    </Link>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="divide-y divide-slate-100/80 md:hidden">
        {rows.map((r) => (
          <li key={r.id} className={cn('p-5 transition-colors hover:bg-slate-50/50', r.escalated && 'shadow-[inset_3px_0_0_#ef4444]')}>
            <Link to={`/requests/${r.id}`} className="block">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-[14px] font-semibold text-slate-900">{showEmployee ? r.employee.name : `#${r.id}`}</p>
                  <p className="mt-0.5 text-[13px] font-medium text-slate-500">{fmtRange(r.startDate, r.endDate)} · {t('common.days', { count: Number(formatDays(r.workingDays)) })}</p>
                </div>
                <LeaveTypeDot code={r.leaveTypeCode} />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <StatusBadge status={r.status} />
                {r.flagged && <FlagBadge />}
              </div>
            </Link>
            {showActions && (
              <div className="mt-4 border-t border-slate-100/80 pt-4">
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
