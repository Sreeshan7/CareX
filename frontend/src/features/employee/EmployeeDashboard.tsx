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
      <h2 className="mb-4 text-[13px] font-bold uppercase tracking-widest text-slate-400">{t('employee.balances')}</h2>
      {balances.isError ? <Card><ErrorState error={balances.error} onRetry={() => balances.refetch()} /></Card> : (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {balances.isLoading ? [0, 1, 2].map((i) => <Skeleton key={i} className="h-36 rounded-2xl" />)
            : balances.data?.map((b) => <BalanceCard key={b.leaveTypeCode} b={b} />)}
        </div>
      )}

      <div className="mt-10 grid gap-8 lg:grid-cols-3">
        <div className="lg:col-span-1 space-y-4">
          <h2 className="text-[13px] font-bold uppercase tracking-widest text-slate-400">{t('employee.upcoming')}</h2>
          <Card className="overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm">
            {upcoming.length === 0 ? <EmptyState title={t('employee.noUpcoming')} icon={<Plane className="h-6 w-6" />} /> : (
              <ul className="divide-y divide-slate-100/80">
                {upcoming.map((r) => (
                  <li key={r.id}>
                    <Link to={`/requests/${r.id}`} className="group block px-6 py-4 transition-colors hover:bg-slate-50/80">
                      <p className="text-[14px] font-semibold tracking-tight text-slate-900 group-hover:text-accent-600">{fmtRange(r.startDate, r.endDate)}</p>
                      <div className="mt-2 flex items-center justify-between gap-3">
                        <span className="text-[12px] font-medium text-slate-500">{t(`leaveType.${r.leaveTypeCode}`)} · {t('common.days', { count: Number(formatDays(r.workingDays)) })}</span>
                        <StatusBadge status={r.status} />
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
        
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-[13px] font-bold uppercase tracking-widest text-slate-400">{t('employee.recent')}</h2>
            <Link to="/me/requests" className="text-[12px] font-semibold text-accent-600 hover:text-accent-700 hover:underline">{t('common.seeAll')}</Link>
          </div>
          <Card className="overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm">
            {requests.isLoading ? <div className="p-6"><Skeleton className="h-48 rounded-xl" /></div>
              : requests.isError ? <ErrorState error={requests.error} onRetry={() => requests.refetch()} />
              : requests.data!.items.length === 0 ? <EmptyState title={t('requests.empty')} />
              : <RequestTable rows={requests.data!.items} showEmployee={false} />}
          </Card>
        </div>
      </div>
    </div>
  )
}
