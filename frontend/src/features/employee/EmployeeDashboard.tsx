import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CalendarPlus, Plane } from 'lucide-react'
import { leaveApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthProvider'
import { BalanceCard } from '../../components/leave/BalanceCard'
import { RequestTable } from '../../components/leave/RequestTable'
import { StatusBadge } from '../../components/leave/StatusBadge'
import { Button, Card, CardHeader, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui/primitives'
import { fmtRange, isoToday } from '../../lib/dates'
import { formatDays } from '../../lib/utils'

export function EmployeeDashboard() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const balances = useQuery({ queryKey: ['me', 'balances'], queryFn: () => leaveApi.balances() })
  const requests = useQuery({ queryKey: ['me', 'requests', 'recent'], queryFn: () => leaveApi.mine(undefined, 0, 8), refetchInterval: 30_000 })
  const today = isoToday()
  const upcoming = (requests.data?.items ?? []).filter((r) =>
    r.endDate >= today && ['APPROVED', 'PENDING_MANAGER', 'PENDING_HR', 'MANAGER_ESCALATED', 'HR_ESCALATED'].includes(r.status))
    .sort((a, b) => a.startDate.localeCompare(b.startDate)).slice(0, 3)

  return (
    <div>
      <PageHeader
        title={t('employee.hello', { name: user?.name.split(' ')[0] })}
        subtitle={`${user?.teamName} · ${t(`roles.${user?.role}`)}`}
        action={<Link to="/me/apply"><Button size="lg"><CalendarPlus className="h-5 w-5" aria-hidden />{t('employee.applyCta')}</Button></Link>}
      />
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">{t('employee.balances')}</h2>
      {balances.isError ? <Card><ErrorState error={balances.error} onRetry={() => balances.refetch()} /></Card> : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {balances.isLoading ? [0, 1, 2].map((i) => <Skeleton key={i} className="h-36" />)
            : balances.data?.map((b) => <BalanceCard key={b.leaveTypeCode} b={b} />)}
        </div>
      )}

      <div className="mt-8 grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader title={t('employee.upcoming')} />
          {upcoming.length === 0 ? <EmptyState title={t('employee.noUpcoming')} icon={<Plane className="h-6 w-6" />} /> : (
            <ul className="divide-y divide-slate-100">
              {upcoming.map((r) => (
                <li key={r.id}>
                  <Link to={`/requests/${r.id}`} className="block px-5 py-3 hover:bg-slate-50">
                    <p className="text-sm font-medium text-slate-900">{fmtRange(r.startDate, r.endDate)}</p>
                    <div className="mt-1 flex items-center justify-between gap-2">
                      <span className="text-xs text-slate-500">{t(`leaveType.${r.leaveTypeCode}`)} · {t('common.days', { count: Number(formatDays(r.workingDays)) })}</span>
                      <StatusBadge status={r.status} />
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader title={t('employee.recent')} action={<Link to="/me/requests" className="text-sm font-medium text-brand-700 hover:underline">{t('common.seeAll')}</Link>} />
          {requests.isLoading ? <div className="p-5"><Skeleton className="h-32" /></div>
            : requests.isError ? <ErrorState error={requests.error} onRetry={() => requests.refetch()} />
            : requests.data!.items.length === 0 ? <EmptyState title={t('requests.empty')} />
            : <RequestTable rows={requests.data!.items} showEmployee={false} />}
        </Card>
      </div>
    </div>
  )
}
