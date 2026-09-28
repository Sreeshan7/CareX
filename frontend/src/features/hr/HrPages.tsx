import * as React from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { AlertOctagon, CheckCircle2, ChevronDown, ChevronRight, Clock, PlayCircle, RotateCcw, ShieldCheck, TriangleAlert, Users } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { toast } from 'sonner'
import { authApi, hrApi } from '../../api/endpoints'
import type { EscalationView } from '../../api/types'
import { RequestTable } from '../../components/leave/RequestTable'
import { StatusBadge } from '../../components/leave/StatusBadge'
import { Alert, Badge, Button, Card, CardHeader, EmptyState, ErrorState, Input, PageHeader, Select, Skeleton, StatCard } from '../../components/ui/primitives'
import { fmtDateTime, fmtRange, humanMinutes } from '../../lib/dates'
import { errorMessage } from '../../lib/errors'
import { cn } from '../../lib/utils'

export function HrDashboard() {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const overview = useQuery({ queryKey: ['hr', 'overview'], queryFn: () => hrApi.overview(), refetchInterval: 30_000 })
  const queue = useQuery({ queryKey: ['hr', 'approvals'], queryFn: hrApi.approvals, refetchInterval: 30_000 })
  const integrity = useQuery({ queryKey: ['hr', 'integrity'], queryFn: hrApi.integrity })
  const demo = useQuery({ queryKey: ['demo-accounts'], queryFn: authApi.demoAccounts, staleTime: Infinity })
  const run = useRunEscalations()
  const reset = useMutation({
    mutationFn: hrApi.demoReset,
    onSuccess: () => { toast.success(t('hr.resetDone')); qc.invalidateQueries() },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const o = overview.data
  return (
    <div>
      <PageHeader title={t('hr.title')}
        action={<>
          <Button variant="outline" onClick={() => run.mutate()} loading={run.isPending}><PlayCircle className="h-4 w-4" aria-hidden />{t('hr.runNow')}</Button>
          {demo.data?.enabled && <Button variant="ghost" onClick={() => reset.mutate()} loading={reset.isPending}><RotateCcw className="h-4 w-4" aria-hidden />{t('hr.reset')}</Button>}
        </>} />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        {!o ? [0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-28" />) : <>
          <StatCard label={t('hr.pendingHr')} value={o.pendingHr} tone="sky" icon={<ShieldCheck className="h-4 w-4" />} />
          <StatCard label={t('hr.openEscalations')} value={o.openEscalations} tone="red" icon={<AlertOctagon className="h-4 w-4" />} />
          <StatCard label={t('hr.flaggedActive')} value={o.flaggedActive} tone="amber" icon={<TriangleAlert className="h-4 w-4" />} />
          <StatCard label={t('hr.avgApproval')} value={t('hr.hours', { count: o.avgApprovalHours })} tone="brand" icon={<Clock className="h-4 w-4" />} />
          <StatCard label={t('hr.headcount')} value={o.headcount} tone="slate" icon={<Users className="h-4 w-4" />} hint={`${o.totalRequests} requests in ${o.year}`} />
        </>}
      </div>
      {integrity.data && (
        <Alert tone={integrity.data.ok ? 'success' : 'error'} className="mt-4" icon={integrity.data.ok ? <CheckCircle2 className="h-4 w-4" /> : <TriangleAlert className="h-4 w-4" />}>
          <span className="font-medium">{t('hr.integrity')}:</span> {integrity.data.ok ? t('hr.integrityOk') : t('hr.integrityBad', { count: integrity.data.mismatches.length })}
        </Alert>
      )}
      <Card className="mt-6">
        <CardHeader title={t('hr.approvalsTitle')} action={<Link to="/hr/approvals" className="text-sm font-medium text-brand-700 hover:underline">{t('common.seeAll')}</Link>} />
        {queue.isLoading ? <div className="p-5"><Skeleton className="h-32" /></div>
          : queue.isError ? <ErrorState error={queue.error} onRetry={() => queue.refetch()} />
          : queue.data!.length === 0 ? <EmptyState title={t('hr.emptyQueue')} />
          : <RequestTable rows={queue.data!.slice(0, 6)} showActions showDeadline />}
      </Card>
      {o && o.upcomingAbsences.length > 0 && (
        <Card className="mt-6">
          <CardHeader title={t('hr.upcoming')} />
          <RequestTable rows={o.upcomingAbsences} />
        </Card>
      )}
    </div>
  )
}

function useRunEscalations() {
  const { t } = useTranslation()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: hrApi.runEscalations,
    onSuccess: (r) => { toast.success(t('hr.ran', { count: r.escalated })); qc.invalidateQueries({ queryKey: ['hr'] }) },
    onError: (e) => toast.error(errorMessage(e)),
  })
}

export function HrApprovalsPage() {
  const { t } = useTranslation()
  const q = useQuery({ queryKey: ['hr', 'approvals'], queryFn: hrApi.approvals, refetchInterval: 30_000 })
  return (
    <div>
      <PageHeader title={t('hr.approvalsTitle')} />
      <Card>
        {q.isLoading ? <div className="p-5"><Skeleton className="h-40" /></div>
          : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : q.data!.length === 0 ? <EmptyState title={t('hr.emptyQueue')} />
          : <RequestTable rows={q.data!} showActions showDeadline />}
      </Card>
    </div>
  )
}

export function EscalationsPage() {
  const { t } = useTranslation()
  const [open, setOpen] = React.useState(true)
  const q = useQuery({ queryKey: ['hr', 'escalations', open], queryFn: () => hrApi.escalations(open), refetchInterval: 30_000 })
  const run = useRunEscalations()
  return (
    <div>
      <PageHeader title={t('hr.escalationsTitle')}
        action={<Button variant="outline" onClick={() => run.mutate()} loading={run.isPending}><PlayCircle className="h-4 w-4" aria-hidden />{t('hr.runNow')}</Button>} />
      <div className="mb-4 flex gap-2" role="tablist">
        {[true, false].map((o) => (
          <button key={String(o)} role="tab" aria-selected={open === o} onClick={() => setOpen(o)}
            className={cn('rounded-full px-3.5 py-1.5 text-sm font-medium ring-1 ring-inset', open === o ? 'bg-brand-700 text-white ring-brand-700' : 'bg-white text-slate-600 ring-slate-200')}>
            {o ? t('hr.open') : t('common.all')}
          </button>
        ))}
      </div>
      <Card>
        {q.isLoading ? <div className="p-5"><Skeleton className="h-40" /></div>
          : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : q.data!.length === 0 ? <EmptyState title={t('common.none')} />
          : <EscalationList rows={q.data!} />}
      </Card>
    </div>
  )
}

function EscalationList({ rows }: { rows: EscalationView[] }) {
  const { t } = useTranslation()
  return (
    <ul className="divide-y divide-slate-100">
      {rows.map((e) => (
        <li key={e.id} className={cn('flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-center sm:justify-between', !e.resolvedAt && 'shadow-[inset_3px_0_0_#ef4444]')}>
          <div>
            <p className="text-sm font-medium text-slate-900">
              <Link to={`/requests/${e.requestId}`} className="hover:underline">#{e.requestId}</Link> · {e.employee.name}
              <span className="font-normal text-slate-500"> · {e.employee.teamName} · {fmtRange(e.startDate, e.endDate)}</span>
            </p>
            <p className="mt-0.5 text-xs text-slate-500">
              {e.stage === 'MANAGER' ? t('stage.manager') : t('stage.hr')} · {t('hr.target')}: {e.target?.name ?? '—'} ({e.targetRole}) · {fmtDateTime(e.escalatedAt)}
              {e.lateBySeconds > 60 && <> · <span className="text-red-600">{t('hr.late', { time: humanMinutes(Math.round(e.lateBySeconds / 60)) })}</span></>}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <StatusBadge status={e.requestStatus} />
            <Badge className={e.resolvedAt ? 'bg-emerald-50 text-emerald-700 ring-emerald-200' : 'bg-red-50 text-red-700 ring-red-200'}>
              {e.resolvedAt ? `${t('hr.resolved')} · ${e.resolution}` : t('hr.open')}
            </Badge>
          </div>
        </li>
      ))}
    </ul>
  )
}

const PALETTE = ['#0d9488', '#8b5cf6', '#f97316', '#0ea5e9', '#e11d48', '#64748b', '#eab308']
const STATUS_COLORS: Record<string, string> = {
  PENDING_MANAGER: '#f59e0b', MANAGER_ESCALATED: '#ef4444', PENDING_HR: '#0ea5e9', HR_ESCALATED: '#dc2626',
  APPROVED: '#10b981', REJECTED: '#e11d48', CANCELLED: '#94a3b8',
}

export function OverviewPage() {
  const { t } = useTranslation()
  const [year, setYear] = React.useState(new Date().getFullYear())
  const q = useQuery({ queryKey: ['hr', 'overview', year], queryFn: () => hrApi.overview(year) })
  const o = q.data
  const toRows = (m?: Record<string, number>) => Object.entries(m ?? {}).map(([k, v]) => ({ name: k, value: Number(v) }))
  return (
    <div>
      <PageHeader title={t('hr.overviewTitle')}
        action={<Select value={year} onChange={(e) => setYear(Number(e.target.value))} className="w-28">
          {[year - 1, year, year + 1].map((y) => <option key={y} value={y}>{y}</option>)}
        </Select>} />
      {q.isError ? <Card><ErrorState error={q.error} onRetry={() => q.refetch()} /></Card> : !o ? <Skeleton className="h-80" /> : (
        <div className="grid gap-6 lg:grid-cols-2">
          <ChartCard title={t('hr.byStatus')}>
            <BarChart data={toRows(o.byStatus).map((r) => ({ ...r, label: t(`status.${r.name}`) }))}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="label" tick={{ fontSize: 11 }} interval={0} angle={-15} textAnchor="end" height={50} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                {toRows(o.byStatus).map((r) => <Cell key={r.name} fill={STATUS_COLORS[r.name] ?? '#64748b'} />)}
              </Bar>
            </BarChart>
          </ChartCard>
          <ChartCard title={t('hr.byType')}>
            <BarChart data={toRows(o.byType).map((r) => ({ ...r, label: t(`leaveType.${r.name}`) }))}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="label" tick={{ fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                {toRows(o.byType).map((r, i) => <Cell key={r.name} fill={PALETTE[i % PALETTE.length]} />)}
              </Bar>
            </BarChart>
          </ChartCard>
          <ChartCard title={t('hr.byTeam')}>
            <BarChart data={toRows(o.activeDaysByTeam)} layout="vertical">
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" tick={{ fontSize: 11 }} />
              <YAxis type="category" dataKey="name" tick={{ fontSize: 12 }} width={100} />
              <Tooltip />
              <Bar dataKey="value" fill="#0d9488" radius={[0, 6, 6, 0]} />
            </BarChart>
          </ChartCard>
          <Card className="p-5">
            <h3 className="text-base font-semibold">{t('hr.upcoming')}</h3>
            {o.upcomingAbsences.length === 0 ? <EmptyState title={t('common.none')} /> : (
              <ul className="mt-3 divide-y divide-slate-100">
                {o.upcomingAbsences.map((r) => (
                  <li key={r.id} className="flex items-center justify-between py-2 text-sm">
                    <Link to={`/requests/${r.id}`} className="font-medium hover:underline">{r.employee.name}</Link>
                    <span className="text-slate-600">{fmtRange(r.startDate, r.endDate)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      )}
    </div>
  )
}

function ChartCard({ title, children }: { title: string; children: React.ReactElement }) {
  return (
    <Card className="p-5">
      <h3 className="mb-4 text-base font-semibold">{title}</h3>
      <div className="h-64"><ResponsiveContainer width="100%" height="100%">{children}</ResponsiveContainer></div>
    </Card>
  )
}

const AUDIT_ACTIONS = ['', 'LEAVE_SUBMITTED', 'LEAVE_MANAGER_APPROVED', 'LEAVE_MANAGER_REJECTED', 'LEAVE_HR_APPROVED', 'LEAVE_HR_REJECTED',
  'LEAVE_CANCELLED', 'LEAVE_ESCALATED', 'CONFLICT_FLAGGED', 'CONFLICT_ACKNOWLEDGED', 'BALANCE_CONSUMED', 'BALANCE_RESTORED',
  'TRANSITION_DENIED', 'ACCESS_DENIED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'DEMO_LOGIN', 'ESCALATION_RUN_MANUAL', 'ASSISTANT_PROPOSAL_CREATED']

export function AuditPage() {
  const { t } = useTranslation()
  const [action, setAction] = React.useState('')
  const [entityId, setEntityId] = React.useState('')
  const [page, setPage] = React.useState(0)
  const [expanded, setExpanded] = React.useState<number | null>(null)
  const q = useQuery({
    queryKey: ['hr', 'audit', action, entityId, page],
    queryFn: () => hrApi.audit({ action: action || undefined, entityId: entityId ? Number(entityId) : undefined, entityType: entityId ? 'LEAVE_REQUEST' : undefined, page, size: 50 }),
  })
  const totalPages = q.data ? Math.max(1, Math.ceil(q.data.total / q.data.size)) : 1
  return (
    <div>
      <PageHeader title={t('hr.auditTitle')} subtitle={t('hr.auditHint')} />
      <Card className="mb-4 flex flex-wrap items-end gap-3 p-4">
        <div className="w-64">
          <label className="mb-1 block text-xs font-medium text-slate-500" htmlFor="audit-action">{t('hr.action')}</label>
          <Select id="audit-action" value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }}>
            {AUDIT_ACTIONS.map((a) => <option key={a} value={a}>{a || t('common.all')}</option>)}
          </Select>
        </div>
        <div className="w-40">
          <label className="mb-1 block text-xs font-medium text-slate-500" htmlFor="audit-req">Request #</label>
          <Input id="audit-req" inputMode="numeric" value={entityId} onChange={(e) => { setEntityId(e.target.value.replace(/\D/g, '')); setPage(0) }} />
        </div>
      </Card>
      <Card>
        {q.isLoading ? <div className="p-5"><Skeleton className="h-60" /></div>
          : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : q.data!.items.length === 0 ? <EmptyState title={t('common.none')} /> : (
            <ul className="divide-y divide-slate-100">
              {q.data!.items.map((a) => (
                <li key={a.id}>
                  <button className="flex w-full items-start gap-3 px-5 py-3 text-left hover:bg-slate-50" onClick={() => setExpanded(expanded === a.id ? null : a.id)} aria-expanded={expanded === a.id}>
                    {expanded === a.id ? <ChevronDown className="mt-0.5 h-4 w-4 text-slate-400" /> : <ChevronRight className="mt-0.5 h-4 w-4 text-slate-400" />}
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-semibold text-slate-700">{a.action}</code>
                        <span className="text-sm text-slate-900">{a.summary}</span>
                      </div>
                      <p className="mt-0.5 text-xs text-slate-500">
                        {fmtDateTime(a.occurredAt)} · {a.actorName} ({a.actorRole}) · {a.channel}
                        {a.entityId && <> · {a.entityType === 'LEAVE_REQUEST' ? <Link className="text-brand-700 hover:underline" to={`/requests/${a.entityId}`} onClick={(e) => e.stopPropagation()}>#{a.entityId}</Link> : `${a.entityType} #${a.entityId}`}</>}
                        {a.correlationId && <> · ref {a.correlationId}</>}
                      </p>
                    </div>
                  </button>
                  {expanded === a.id && (
                    <div className="grid gap-3 px-12 pb-4 sm:grid-cols-2">
                      <pre className="overflow-x-auto rounded-lg bg-slate-50 p-3 text-xs text-slate-700">before: {JSON.stringify(a.beforeState, null, 2)}</pre>
                      <pre className="overflow-x-auto rounded-lg bg-slate-50 p-3 text-xs text-slate-700">after: {JSON.stringify(a.afterState, null, 2)}</pre>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
      </Card>
      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-end gap-2 text-sm">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>‹</Button>
          <span className="text-slate-600">{page + 1} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>›</Button>
        </div>
      )}
    </div>
  )
}
