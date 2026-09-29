import * as React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { AlertOctagon, CalendarDays, ClipboardCheck, TriangleAlert, UserX, Users } from 'lucide-react'
import { managerApi } from '../../api/endpoints'
import { RequestTable } from '../../components/leave/RequestTable'
import { TeamCalendarGrid } from '../../components/leave/TeamCalendarGrid'
import { Button, Card, CardHeader, EmptyState, ErrorState, PageHeader, Skeleton, StatCard } from '../../components/ui/primitives'
import { addDaysIso, isoToday } from '../../lib/dates'
import { cn } from '../../lib/utils'

export function ManagerDashboard() {
  const { t } = useTranslation()
  const q = useQuery({ queryKey: ['manager', 'dashboard'], queryFn: managerApi.dashboard, refetchInterval: 30_000 })
  if (q.isError) return <Card><ErrorState error={q.error} onRetry={() => q.refetch()} /></Card>
  const d = q.data
  return (
    <div>
      <PageHeader title={t('manager.title')} subtitle={d?.teams.map((x) => x.name).join(', ')}
        action={<Link to="/manager/approvals"><Button><ClipboardCheck className="h-4 w-4" aria-hidden />{t('nav.approvals')}</Button></Link>} />
      <div className="mt-8 grid grid-cols-2 gap-5 lg:grid-cols-5">
        {!d ? [0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-28 rounded-2xl" />) : <>
          <StatCard label={t('manager.pending')} value={d.pending} tone="amber" icon={<ClipboardCheck className="h-5 w-5" />} />
          <StatCard label={t('manager.escalated')} value={d.escalated} tone="red" icon={<AlertOctagon className="h-5 w-5" />} />
          <StatCard label={t('manager.flagged')} value={d.flagged} tone="amber" icon={<TriangleAlert className="h-5 w-5" />} />
          <StatCard label={t('manager.outToday')} value={d.outToday} tone="sky" icon={<UserX className="h-5 w-5" />} />
          <StatCard label={t('manager.teamSize')} value={d.teamSize} tone="brand" icon={<Users className="h-5 w-5" />} hint={`${t('manager.outWeek')}: ${d.outThisWeek}`} />
        </>}
      </div>
      <Card className="mt-10 overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm">
        <CardHeader title={t('manager.queue')} action={<Link to="/manager/approvals" className="text-[13px] font-semibold text-accent-600 hover:text-accent-700 hover:underline">{t('common.seeAll')}</Link>} />
        {!d ? <div className="p-6"><Skeleton className="h-48 rounded-xl" /></div>
          : d.queuePreview.length === 0 ? <EmptyState title={t('manager.emptyQueue')} />
          : <RequestTable rows={d.queuePreview} showActions showDeadline />}
      </Card>
      <Card className="mt-10 border-0 ring-1 ring-slate-200/60 shadow-sm">
        <CardHeader title={t('manager.calendarTitle')} subtitle={t('manager.calendarHint')}
          action={<Link to="/manager/team" className="text-[13px] font-semibold text-accent-600 hover:text-accent-700 hover:underline"><CalendarDays className="inline h-4 w-4 mr-1" aria-hidden />{t('common.open')}</Link>} />
        <div className="p-6"><CalendarBlock days={14} /></div>
      </Card>
    </div>
  )
}

function CalendarBlock({ days }: { days: number }) {
  const from = isoToday()
  const to = addDaysIso(from, days - 1)
  const q = useQuery({ queryKey: ['manager', 'team', from, to], queryFn: () => managerApi.team(from, to) })
  if (q.isLoading) return <Skeleton className="h-40" />
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />
  return <TeamCalendarGrid data={q.data!} />
}

export function ManagerApprovalsPage() {
  const { t } = useTranslation()
  const [scope, setScope] = React.useState<'pending' | 'decided'>('pending')
  const q = useQuery({ queryKey: ['manager', 'approvals', scope], queryFn: () => managerApi.approvals(scope), refetchInterval: 30_000 })
  return (
    <div>
      <PageHeader title={t('nav.approvals')} />
      <div className="mb-6 flex gap-2" role="tablist">
        {(['pending', 'decided'] as const).map((s) => (
          <button key={s} role="tab" aria-selected={scope === s} onClick={() => setScope(s)}
            className={cn('rounded-lg px-4 py-2 text-[13px] font-semibold ring-1 ring-inset transition-all duration-200', scope === s ? 'bg-slate-900 text-white ring-slate-900 shadow-sm' : 'bg-white text-slate-600 ring-slate-200/80 hover:bg-slate-50 hover:text-slate-900')}>
            {s === 'pending' ? t('manager.pending') : t('manager.decided')}
          </button>
        ))}
      </div>
      <Card className="overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm">
        {q.isLoading ? <div className="p-6"><Skeleton className="h-48 rounded-xl" /></div>
          : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : q.data!.length === 0 ? <EmptyState title={t('manager.emptyQueue')} />
          : <RequestTable rows={q.data!} showActions={scope === 'pending'} showDeadline={scope === 'pending'} />}
      </Card>
    </div>
  )
}

export function ManagerTeamPage() {
  const { t } = useTranslation()
  const [offset, setOffset] = React.useState(0)
  const from = addDaysIso(isoToday(), offset)
  const to = addDaysIso(from, 27)
  const q = useQuery({ queryKey: ['manager', 'team', from, to], queryFn: () => managerApi.team(from, to) })
  return (
    <div>
      <PageHeader title={t('manager.calendarTitle')} subtitle={t('manager.calendarHint')}
        action={<>
          <Button variant="outline" size="sm" onClick={() => setOffset((o) => o - 28)}>‹</Button>
          <Button variant="outline" size="sm" onClick={() => setOffset(0)}>{t('common.refresh')}</Button>
          <Button variant="outline" size="sm" onClick={() => setOffset((o) => o + 28)}>›</Button>
        </>} />
      <Card className="p-6 border-0 ring-1 ring-slate-200/60 shadow-sm">
        {q.isLoading ? <Skeleton className="h-64 rounded-xl" /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : <TeamCalendarGrid data={q.data!} />}
      </Card>
    </div>
  )
}

export function ManagerConflictsPage() {
  const { t } = useTranslation()
  const q = useQuery({ queryKey: ['manager', 'conflicts'], queryFn: managerApi.conflicts, refetchInterval: 60_000 })
  return (
    <div>
      <PageHeader title={t('manager.conflictsTitle')} />
      <Card className="overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm">
        {q.isLoading ? <div className="p-6"><Skeleton className="h-48 rounded-xl" /></div>
          : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : q.data!.length === 0 ? <EmptyState title={t('manager.conflictsEmpty')} />
          : <RequestTable rows={q.data!} showActions />}
      </Card>
    </div>
  )
}
