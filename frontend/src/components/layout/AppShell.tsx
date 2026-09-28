import * as React from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  AlertOctagon, BarChart3, CalendarDays, CalendarPlus, ClipboardCheck, FileClock, LayoutDashboard, ListChecks,
  LogOut, Menu, ScrollText, ShieldCheck, TriangleAlert, X,
} from 'lucide-react'
import { useAuth } from '../../auth/AuthProvider'
import { cn } from '../../lib/utils'
import { NotificationBell } from './NotificationBell'
import { LanguageSwitcher } from './LanguageSwitcher'
import { AssistantLauncher } from '../assistant/AssistantPanel'
import { useQuery } from '@tanstack/react-query'
import { authApi } from '../../api/endpoints'

interface NavItem { to: string; label: string; icon: React.ReactNode; end?: boolean }

export function AppShell() {
  const { t } = useTranslation()
  const { user, logout } = useAuth()
  const [open, setOpen] = React.useState(false)
  const loc = useLocation()
  const navigate = useNavigate()
  const demo = useQuery({ queryKey: ['demo-accounts'], queryFn: authApi.demoAccounts, staleTime: Infinity })
  React.useEffect(() => setOpen(false), [loc.pathname])
  if (!user) return null

  const sections: { title: string; items: NavItem[] }[] = []
  if (user.role === 'MANAGER') {
    sections.push({ title: t('nav.manager'), items: [
      { to: '/manager', label: t('nav.dashboard'), icon: <LayoutDashboard className="h-4 w-4" />, end: true },
      { to: '/manager/approvals', label: t('nav.approvals'), icon: <ClipboardCheck className="h-4 w-4" /> },
      { to: '/manager/team', label: t('nav.teamCalendar'), icon: <CalendarDays className="h-4 w-4" /> },
      { to: '/manager/conflicts', label: t('nav.conflicts'), icon: <TriangleAlert className="h-4 w-4" /> },
    ] })
  }
  if (user.role === 'HR') {
    sections.push({ title: t('nav.hr'), items: [
      { to: '/hr', label: t('nav.dashboard'), icon: <LayoutDashboard className="h-4 w-4" />, end: true },
      { to: '/hr/approvals', label: t('nav.hrApprovals'), icon: <ShieldCheck className="h-4 w-4" /> },
      { to: '/hr/escalations', label: t('nav.escalations'), icon: <AlertOctagon className="h-4 w-4" /> },
      { to: '/hr/overview', label: t('nav.overview'), icon: <BarChart3 className="h-4 w-4" /> },
      { to: '/hr/audit', label: t('nav.audit'), icon: <ScrollText className="h-4 w-4" /> },
    ] })
  }
  sections.push({ title: t('nav.myLeave'), items: [
    { to: '/me', label: user.role === 'EMPLOYEE' ? t('nav.dashboard') : t('nav.myLeave'), icon: <ListChecks className="h-4 w-4" />, end: true },
    { to: '/me/apply', label: t('nav.apply'), icon: <CalendarPlus className="h-4 w-4" /> },
    { to: '/me/requests', label: t('nav.requests'), icon: <FileClock className="h-4 w-4" /> },
  ] })

  const nav = (
    <nav aria-label="Main" className="flex flex-1 flex-col gap-6 overflow-y-auto px-3 py-4">
      {sections.map((s) => (
        <div key={s.title}>
          <p className="px-3 pb-1.5 text-xs font-semibold uppercase tracking-wider text-slate-400">{s.title}</p>
          <ul className="space-y-0.5">
            {s.items.map((it) => (
              <li key={it.to}>
                <NavLink
                  to={it.to}
                  end={it.end}
                  className={({ isActive }) => cn('flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                    isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900')}
                >
                  {it.icon}{it.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )

  const brand = (
    <div className="flex h-16 items-center gap-2.5 border-b border-slate-100 px-5">
      <img src="/favicon.svg" alt="" className="h-8 w-8" />
      <div>
        <p className="text-sm font-semibold leading-tight text-slate-900">{t('app.name')}</p>
        <p className="text-xs leading-tight text-slate-500">{t('app.tagline')}</p>
      </div>
    </div>
  )

  const userBox = (
    <div className="border-t border-slate-100 p-3">
      <div className="flex items-center gap-3 rounded-xl px-2 py-2">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-800" aria-hidden>
          {user.name.split(' ').map((p) => p[0]).slice(0, 2).join('')}
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-slate-900">{user.name}</p>
          <p className="truncate text-xs text-slate-500">{t(`roles.${user.role}`)} · {user.teamName}</p>
        </div>
        <button onClick={() => { logout(); navigate('/login') }} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label={t('nav.logout')} title={t('nav.logout')}>
          <LogOut className="h-4 w-4" />
        </button>
      </div>
    </div>
  )

  return (
    <div className="flex min-h-full">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-slate-200 bg-white md:flex">
        {brand}{nav}{userBox}
      </aside>
      {open && (
        <div className="fixed inset-0 z-40 md:hidden" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-slate-900/40" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-72 flex-col bg-white shadow-xl">
            <button className="absolute right-3 top-4 rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" onClick={() => setOpen(false)} aria-label={t('common.close')}>
              <X className="h-5 w-5" />
            </button>
            {brand}{nav}{userBox}
          </aside>
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col md:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-2 border-b border-slate-200 bg-white/85 px-4 backdrop-blur sm:px-6">
          <button className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 md:hidden" onClick={() => setOpen(true)} aria-label="Open menu">
            <Menu className="h-5 w-5" />
          </button>
          <div className="flex-1">
            {demo.data?.enabled && (
              <span className="hidden rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-800 sm:inline">{t('common.demoRibbon')}</span>
            )}
          </div>
          <LanguageSwitcher />
          <NotificationBell />
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
      <AssistantLauncher />
    </div>
  )
}
