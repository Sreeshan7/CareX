import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Clock, MessageSquare, Mic } from 'lucide-react'
import { leaveApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { StageStepper } from '../../components/leave/StageStepper'
import { Timeline } from '../../components/leave/Timeline'
import { ConflictPanel } from '../../components/leave/ConflictPanel'
import { RequestActions } from '../../components/leave/RequestActions'
import { DeadlineBadge, FlagBadge, LeaveTypeDot, StatusBadge } from '../../components/leave/StatusBadge'
import { Alert, Card, CardHeader, ErrorState, Skeleton } from '../../components/ui/primitives'
import { fmtDateTime, fmtRange, humanMinutes } from '../../lib/dates'
import { formatDays } from '../../lib/utils'
import { NotFoundPage } from './NotFoundPage'

export function RequestDetailPage() {
  const { t } = useTranslation()
  const id = Number(useParams().id)
  const q = useQuery({ queryKey: ['request', id], queryFn: () => leaveApi.detail(id), refetchInterval: 30_000, enabled: Number.isFinite(id) })

  if (q.isLoading) return <div className="space-y-4"><Skeleton className="h-10 w-64" /><Skeleton className="h-40" /><Skeleton className="h-64" /></div>
  if (q.error instanceof ApiError && q.error.status === 404) return <NotFoundPage />
  if (q.isError || !q.data) return <Card><ErrorState error={q.error} onRetry={() => q.refetch()} /></Card>
  const r = q.data
  const openEscalation = r.escalations.find((e) => !e.resolvedAt)

  return (
    <div className="space-y-6">
      <Link to={-1 as unknown as string} onClick={(e) => { e.preventDefault(); history.back() }} className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden />{t('common.back')}
      </Link>
      <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between animate-fadeIn">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-[28px] font-bold tracking-tight text-slate-900">{t('detail.title', { id: r.id })}</h1>
            <StatusBadge status={r.status} />
            {r.conflict?.flagged && <FlagBadge />}
            {!r.status.endsWith('ESCALATED') && <DeadlineBadge deadline={r.stageDeadlineAt} />}
            {r.channel === 'VOICE' && <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider text-slate-600 ring-1 ring-slate-200/60"><Mic className="h-3.5 w-3.5 text-accent-500" aria-hidden />{t('common.voice')}</span>}
            {r.channel === 'CHAT' && <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider text-slate-600 ring-1 ring-slate-200/60"><MessageSquare className="h-3.5 w-3.5 text-accent-500" aria-hidden />{t('common.chat')}</span>}
          </div>
          <p className="mt-2 text-[14px] text-slate-500">
            <span className="font-semibold text-slate-900">{r.employee.name}</span> · {r.employee.teamName} · {fmtRange(r.startDate, r.endDate)} · <span className="font-medium text-slate-700">{t('common.days', { count: Number(formatDays(r.workingDays)) })}</span>
          </p>
        </div>
        <RequestActions allowed={r.allowedActions}
          target={{ id: r.id, employeeName: r.employee.name, flagged: !!r.conflict?.flagged, range: fmtRange(r.startDate, r.endDate) }} />
      </div>

      <Card className="p-6 border-0 ring-1 ring-slate-200/60 shadow-sm"><StageStepper request={r} /></Card>

      {openEscalation && (
        <Alert tone="error" icon={<Clock className="h-4 w-4" />}>
          <p className="font-medium">{t('common.escalated')} · {openEscalation.stage === 'MANAGER' ? t('stage.manager') : t('stage.hr')}</p>
          <p>
            {t('detail.escalatedTo')}: {openEscalation.target?.name ?? t('detail.hrPool')} ({openEscalation.targetRole}) · {fmtDateTime(openEscalation.escalatedAt)}
            {openEscalation.lateBySeconds > 60 && ` · ${t('hr.late', { time: humanMinutes(Math.round(openEscalation.lateBySeconds / 60)) })}`}
          </p>
        </Alert>
      )}

      <div className="grid gap-8 lg:grid-cols-3">
        <Card className="lg:col-span-2 overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm h-fit">
          <CardHeader title={t('detail.conflict')} />
          <div className="p-6">{r.conflict ? <ConflictPanel conflict={r.conflict} /> : null}</div>
        </Card>
        <Card className="overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm h-fit">
          <CardHeader title={t('common.dates')} />
          <dl className="space-y-4 p-6 text-[13px]">
            <div><dt className="mb-1 text-[11px] font-bold uppercase tracking-widest text-slate-400">{t('common.type')}</dt><dd><LeaveTypeDot code={r.leaveTypeCode} /></dd></div>
            <div><dt className="mb-1 text-[11px] font-bold uppercase tracking-widest text-slate-400">{t('common.reason')}</dt><dd className="font-medium text-slate-900">{r.reason ?? '—'}</dd></div>
            <div>
              <dt className="mb-1 text-[11px] font-bold uppercase tracking-widest text-slate-400">{t('detail.approver')}</dt>
              <dd className="font-medium text-slate-900">{r.managerApprover?.name}{r.managerRoutedToHr && <span className="mt-1 block text-[11px] text-slate-500">{t('detail.routedHr')}</span>}</dd>
            </div>
            {r.escalationApprover && <div><dt className="mb-1 text-[11px] font-bold uppercase tracking-widest text-slate-400">{t('detail.escalatedTo')}</dt><dd className="font-medium text-slate-900">{r.escalationApprover.name}</dd></div>}
            {r.escalatedToHrPool && <div><dt className="mb-1 text-[11px] font-bold uppercase tracking-widest text-slate-400">{t('detail.escalatedTo')}</dt><dd className="font-medium text-slate-900">{t('detail.hrPool')}</dd></div>}
            {r.stageDeadlineAt && <div><dt className="mb-1 text-[11px] font-bold uppercase tracking-widest text-slate-400">{t('detail.deadline')}</dt><dd className="font-medium text-slate-900">{fmtDateTime(r.stageDeadlineAt)}</dd></div>}
          </dl>
        </Card>
      </div>

      <Card className="overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm">
        <CardHeader title={t('detail.timeline')} />
        <div className="p-8"><Timeline entries={r.timeline} /></div>
      </Card>
    </div>
  )
}
