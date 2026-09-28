import * as React from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { notificationApi } from '../../api/endpoints'
import { ago } from '../../lib/dates'
import { cn } from '../../lib/utils'

/** Polls the unread count every 30s (implementation.md D9: polling instead of websockets). */
export function NotificationBell() {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const nav = useNavigate()
  const [open, setOpen] = React.useState(false)
  const ref = React.useRef<HTMLDivElement>(null)
  const count = useQuery({ queryKey: ['notifications', 'count'], queryFn: notificationApi.count, refetchInterval: 30_000 })
  const list = useQuery({ queryKey: ['notifications', 'list'], queryFn: () => notificationApi.list(false), enabled: open })
  const readAll = useMutation({
    mutationFn: notificationApi.readAll,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })

  React.useEffect(() => {
    const onDoc = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  const n = count.data?.count ?? 0
  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative rounded-lg p-2 text-slate-600 hover:bg-slate-100"
        aria-label={`${t('notifications.title')} (${n})`}
        aria-expanded={open}
      >
        <Bell className="h-5 w-5" />
        {n > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white">
            {n > 99 ? '99+' : n}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-40 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold">{t('notifications.title')}</p>
            <button className="text-xs font-medium text-brand-700 hover:underline disabled:opacity-50" disabled={n === 0} onClick={() => readAll.mutate()}>
              {t('notifications.markAll')}
            </button>
          </div>
          <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto">
            {list.isLoading && <li className="px-4 py-6 text-center text-sm text-slate-500">{t('common.loading')}</li>}
            {list.data?.length === 0 && <li className="px-4 py-6 text-center text-sm text-slate-500">{t('notifications.empty')}</li>}
            {list.data?.map((item) => (
              <li key={item.id}>
                <button
                  className={cn('block w-full px-4 py-3 text-left hover:bg-slate-50', !item.readAt && 'bg-brand-50/40')}
                  onClick={async () => {
                    setOpen(false)
                    if (!item.readAt) { await notificationApi.read(item.id).catch(() => undefined); qc.invalidateQueries({ queryKey: ['notifications'] }) }
                    if (item.requestId) nav(`/requests/${item.requestId}`)
                  }}
                >
                  <p className="text-sm font-medium text-slate-900">{item.title}</p>
                  <p className="mt-0.5 line-clamp-2 text-xs text-slate-600">{item.body}</p>
                  <p className="mt-1 text-[11px] text-slate-400">{ago(item.createdAt)}</p>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
