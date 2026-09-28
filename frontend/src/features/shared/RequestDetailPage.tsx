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
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{t('detail.title', { id: r.id })}</h1>
            <StatusBadge status={r.status} />
            {r.conflict?.flagged && <FlagBadge />}
            {!r.status.endsWith('ESCALATED') && <DeadlineBadge deadline={r.stageDeadlineAt} />}
            {r.channel === 'VOICE' && <span className="inline-flex items-center gap-1 text-xs text-brand-700"><Mic className="h-3.5 w-3.5" aria-hidden />{t('common.voice')}</span>}
            {r.channel === 'CHAT' && <span className="inline-flex items-center gap-1 text-xs text-brand-700"><MessageSquare className="h-3.5 w-3.5" aria-hidden />{t('common.chat')}</span>}
          </div>
          <p className="mt-1 text-slate-600">
            <span className="font-medium text-slate-900">{r.employee.name}</span> · {r.employee.teamName} · {fmtRange(r.startDate, r.endDate)} · {t('common.days', { count: Number(formatDays(r.workingDays)) })}
          </p>
        </div>
        <RequestActions allowed={r.allowedActions}
          target={{ id: r.id, employeeName: r.employee.name, flagged: !!r.conflict?.flagged, range: fmtRange(r.startDate, r.endDate) }} />
      </div>

      <Card className="p-5 sm:p-6"><StageStepper request={r} /></Card>

      {openEscalation && (
        <Alert tone="error" icon={<Clock className="h-4 w-4" />}>
          <p className="font-medium">{t('common.escalated')} · {openEscalation.stage === 'MANAGER' ? t('stage.manager') : t('stage.hr')}</p>
          <p>
            {t('detail.escalatedTo')}: {openEscalation.target?.name ?? t('detail.hrPool')} ({openEscalation.targetRole}) · {fmtDateTime(openEscalation.escalatedAt)}
            {openEscalation.lateBySeconds > 60 && ` · ${t('hr.late', { time: humanMinutes(Math.round(openEscalation.lateBySeconds / 60)) })}`}
          </p>
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title={t('detail.conflict')} />
          <div className="p-5">{r.conflict ? <ConflictPanel conflict={r.conflict} /> : null}</div>
        </Card>
        <Card>
          <CardHeader title={t('common.dates')} />
          <dl className="space-y-3 p-5 text-sm">
            <div><dt className="text-slate-500">{t('common.type')}</dt><dd><LeaveTypeDot code={r.leaveTypeCode} /></dd></div>
            <div><dt className="text-slate-500">{t('common.reason')}</dt><dd className="text-slate-900">{r.reason ?? '—'}</dd></div>
            <div>
              <dt className="text-slate-500">{t('detail.approver')}</dt>
              <dd className="text-slate-900">{r.managerApprover?.name}{r.managerRoutedToHr && <span className="block text-xs text-slate-500">{t('detail.routedHr')}</span>}</dd>
            </div>
            {r.escalationApprover && <div><dt className="text-slate-500">{t('detail.escalatedTo')}</dt><dd>{r.escalationApprover.name}</dd></div>}
            {r.escalatedToHrPool && <div><dt className="text-slate-500">{t('detail.escalatedTo')}</dt><dd>{t('detail.hrPool')}</dd></div>}
            {r.stageDeadlineAt && <div><dt className="text-slate-500">{t('detail.deadline')}</dt><dd>{fmtDateTime(r.stageDeadlineAt)}</dd></div>}
          </dl>
        </Card>
      </div>

      <Card>
        <CardHeader title={t('detail.timeline')} />
        <div className="p-6"><Timeline entries={r.timeline} /></div>
      </Card>
    </div>
  )
}
