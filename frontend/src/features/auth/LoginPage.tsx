import * as React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { AlertOctagon, ArrowRight, GitBranch, Languages, LogIn, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { authApi } from '../../api/endpoints'
import { homeFor, useAuth } from '../../auth/AuthProvider'
import { errorMessage } from '../../lib/errors'
import { Alert, Button, Card, Input, Label } from '../../components/ui/primitives'
import { LanguageSwitcher } from '../../components/layout/LanguageSwitcher'
import { cn } from '../../lib/utils'

const ROLE_TONE: Record<string, string> = {
  EMPLOYEE: 'bg-slate-100 text-slate-700', MANAGER: 'bg-sky-100 text-sky-800', HR: 'bg-violet-100 text-violet-800',
}

export function LoginPage() {
  const { t } = useTranslation()
  const { user, login, demoLogin } = useAuth()
  const nav = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = React.useState('')
  const [password, setPassword] = React.useState('')
  const [busy, setBusy] = React.useState<string | null>(null)
  const [error, setError] = React.useState<string | null>(null)
  const demo = useQuery({ queryKey: ['demo-accounts'], queryFn: authApi.demoAccounts, staleTime: 60_000 })

  if (user) return <Navigate to={homeFor(user.role)} replace />

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy('form'); setError(null)
    try {
      const u = await login(email, password)
      nav(homeFor(u.role))
    } catch (err) {
      setError(errorMessage(err))
    } finally { setBusy(null) }
  }

  const quick = async (key: string) => {
    setBusy(key)
    try {
      const u = await demoLogin(key)
      nav(homeFor(u.role))
    } catch (err) {
      toast.error(errorMessage(err))
    } finally { setBusy(null) }
  }

  return (
    <div className="min-h-full bg-gradient-to-br from-brand-50 via-white to-slate-100">
      <div className="mx-auto flex max-w-6xl justify-end px-4 pt-4"><LanguageSwitcher /></div>
      <div className="mx-auto grid max-w-6xl gap-10 px-4 pb-12 pt-6 lg:grid-cols-2 lg:pt-12">
        <section className="flex flex-col justify-center">
          <div className="flex items-center gap-3">
            <img src="/favicon.svg" alt="" className="h-11 w-11" />
            <p className="text-2xl font-semibold tracking-tight text-slate-900">{t('app.name')}</p>
          </div>
          <h1 className="mt-6 text-4xl font-semibold leading-tight tracking-tight text-slate-900">{t('app.tagline')}</h1>
          <p className="mt-3 max-w-md text-slate-600">{t('login.subtitle')}</p>
          <ul className="mt-8 grid max-w-md gap-3 sm:grid-cols-2">
            {[
              { icon: <GitBranch className="h-4 w-4" />, label: t('login.featureChain') },
              { icon: <TriangleAlert className="h-4 w-4" />, label: t('login.featureFlags') },
              { icon: <AlertOctagon className="h-4 w-4" />, label: t('login.featureEscalation') },
              { icon: <Languages className="h-4 w-4" />, label: t('login.featureVoice') },
            ].map((f) => (
              <li key={f.label} className="flex items-center gap-2.5 rounded-xl bg-white/70 px-3 py-2.5 text-sm font-medium text-slate-700 ring-1 ring-slate-200">
                <span className="rounded-lg bg-brand-100 p-1.5 text-brand-700">{f.icon}</span>{f.label}
              </li>
            ))}
          </ul>
        </section>

        <section className="space-y-5">
          {params.get('expired') && <Alert tone="warn">{t('login.expired')}</Alert>}
          {demo.data?.enabled && demo.data.accounts.length > 0 && (
            <Card className="p-5">
              <h2 className="text-base font-semibold">{t('login.demoTitle')}</h2>
              <p className="text-sm text-slate-500">{t('login.demoSubtitle')}</p>
              <ul className="mt-4 grid gap-2 sm:grid-cols-2">
                {demo.data.accounts.map((a) => (
                  <li key={a.key}>
                    <button
                      onClick={() => quick(a.key)}
                      disabled={!!busy}
                      className="group flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-left transition hover:border-brand-300 hover:bg-brand-50/50 disabled:opacity-60"
                    >
                      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-800">
                        {a.name.split(' ').map((p) => p[0]).slice(0, 2).join('')}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center gap-1.5">
                          <span className="truncate text-sm font-medium text-slate-900">{a.name}</span>
                          <span className={cn('rounded px-1.5 py-0.5 text-[10px] font-semibold', ROLE_TONE[a.role])}>{t(`roles.${a.role}`)}</span>
                        </span>
                        <span className="block truncate text-xs text-slate-500">{a.description}</span>
                      </span>
                      <ArrowRight className="h-4 w-4 text-slate-300 group-hover:text-brand-600" aria-hidden />
                    </button>
                  </li>
                ))}
              </ul>
            </Card>
          )}
          <Card className="p-5">
            <h2 className="mb-4 text-base font-semibold">{t('login.title')}</h2>
            <form onSubmit={submit} className="space-y-4">
              <div>
                <Label htmlFor="email">{t('login.email')}</Label>
                <Input id="email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              <div>
                <Label htmlFor="password">{t('login.password')}</Label>
                <Input id="password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
              </div>
              {error && <Alert tone="error">{error}</Alert>}
              <Button type="submit" className="w-full justify-center" loading={busy === 'form'}>
                <LogIn className="h-4 w-4" aria-hidden />{t('login.submit')}
              </Button>
            </form>
          </Card>
        </section>
      </div>
    </div>
  )
}
