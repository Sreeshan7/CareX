import * as React from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { leaveApi } from '../../api/endpoints'
import { RequestTable } from '../../components/leave/RequestTable'
import { Button, Card, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui/primitives'
import { cn } from '../../lib/utils'

const FILTERS = [
  { key: 'all', status: undefined, label: 'requests.filterAll' },
  { key: 'pending', status: 'PENDING', label: 'requests.filterPending' },
  { key: 'approved', status: 'APPROVED', label: 'requests.filterApproved' },
  { key: 'closed', status: 'REJECTED,CANCELLED', label: 'requests.filterClosed' },
]

export function MyRequestsPage() {
  const { t } = useTranslation()
  const [filter, setFilter] = React.useState(FILTERS[0])
  const [page, setPage] = React.useState(0)
  const q = useQuery({ queryKey: ['me', 'requests', filter.key, page], queryFn: () => leaveApi.mine(filter.status, page, 20), refetchInterval: 30_000 })
  const totalPages = q.data ? Math.max(1, Math.ceil(q.data.total / q.data.size)) : 1
  return (
    <div>
      <PageHeader title={t('requests.title')} />
      <div className="mb-6 flex flex-wrap gap-2" role="tablist">
        {FILTERS.map((f) => (
          <button key={f.key} role="tab" aria-selected={filter.key === f.key}
            onClick={() => { setFilter(f); setPage(0) }}
            className={cn('rounded-lg px-4 py-2 text-[13px] font-semibold ring-1 ring-inset transition-all duration-200', filter.key === f.key ? 'bg-slate-900 text-white ring-slate-900 shadow-sm' : 'bg-white text-slate-600 ring-slate-200/80 hover:bg-slate-50 hover:text-slate-900')}>
            {t(f.label)}
          </button>
        ))}
      </div>
      <Card className="overflow-hidden border-0 ring-1 ring-slate-200/60 shadow-sm">
        {q.isLoading ? <div className="p-5"><Skeleton className="h-40" /></div>
          : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : q.data!.items.length === 0 ? <EmptyState title={t('requests.empty')} />
          : <RequestTable rows={q.data!.items} showEmployee={false} />}
      </Card>
      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-end gap-3 text-[13px]">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>‹</Button>
          <span className="font-medium text-slate-500">{page + 1} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>›</Button>
        </div>
      )}
    </div>
  )
}
