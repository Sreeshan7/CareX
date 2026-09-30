import * as React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { AlertOctagon, ArrowRight, GitBranch, Languages, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { authApi } from '../../api/endpoints'
import { homeFor, useAuth } from '../../auth/AuthProvider'
import { errorMessage } from '../../lib/errors'
import { Alert, Button, Input, Label } from '../../components/ui/primitives'
import { LanguageSwitcher } from '../../components/layout/LanguageSwitcher'
import { cn } from '../../lib/utils'

const ROLE_TONE: Record<string, string> = {
  EMPLOYEE: 'bg-slate-100 text-slate-700 ring-slate-200/50', 
  MANAGER: 'bg-sky-50 text-sky-700 ring-sky-200/50', 
  HR: 'bg-violet-50 text-violet-700 ring-violet-200/50',
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
    <div className="flex min-h-screen w-full bg-white">
      {/* Left side - Branding & Features */}
      <div className="relative hidden w-[45%] flex-col justify-between overflow-hidden bg-slate-900 p-12 text-white lg:flex">
        {/* Subtle background pattern */}
        <div className="absolute inset-0 bg-[url('https://grainy-gradients.vercel.app/noise.svg')] opacity-20 mix-blend-overlay"></div>
        <div className="absolute -left-[10%] -top-[10%] h-[50%] w-[50%] rounded-full bg-accent-500/20 blur-[120px]"></div>
        <div className="absolute -right-[10%] -bottom-[10%] h-[50%] w-[50%] rounded-full bg-indigo-500/20 blur-[120px]"></div>

        <div className="relative z-10">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 shadow-glass backdrop-blur-md ring-1 ring-white/20">
              <img src="/favicon.svg" alt="" className="h-6 w-6 brightness-0 invert" />
            </div>
            <p className="text-xl font-bold tracking-tight">{t('app.name')}</p>
          </div>
        </div>

        <div className="relative z-10 mt-auto">
          <h1 className="text-balance text-[40px] font-bold leading-[1.1] tracking-tight">
            {t('app.tagline')}
          </h1>
          <p className="mt-6 max-w-md text-[15px] leading-relaxed text-slate-300">
            {t('login.subtitle')}
          </p>

          <div className="mt-12 grid grid-cols-2 gap-x-8 gap-y-6 border-t border-slate-700/50 pt-10">
            {[
              { icon: <GitBranch className="h-4 w-4" />, label: t('login.featureChain') },
              { icon: <TriangleAlert className="h-4 w-4" />, label: t('login.featureFlags') },
              { icon: <AlertOctagon className="h-4 w-4" />, label: t('login.featureEscalation') },
              { icon: <Languages className="h-4 w-4" />, label: t('login.featureVoice') },
            ].map((f) => (
              <div key={f.label} className="flex items-center gap-3">
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-white/5 text-slate-300 ring-1 ring-white/10">
                  {f.icon}
                </div>
                <span className="text-[13px] font-medium text-slate-300">{f.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right side - Login Form */}
      <div className="flex flex-1 flex-col bg-[#FAFAFA] relative">
        <div className="absolute right-6 top-6 z-10"><LanguageSwitcher /></div>
        
        <div className="flex flex-1 flex-col justify-center px-6 py-12 sm:px-12 lg:px-24">
          <div className="mx-auto w-full max-w-[420px] animate-fadeIn">
            {/* Mobile branding */}
            <div className="mb-10 flex items-center gap-3 lg:hidden">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-900 shadow-sm">
                <img src="/favicon.svg" alt="" className="h-6 w-6 brightness-0 invert" />
              </div>
              <p className="text-2xl font-bold tracking-tight text-slate-900">{t('app.name')}</p>
            </div>

            <div className="mb-8">
              <h2 className="text-[28px] font-bold tracking-tight text-slate-900">{t('login.title')}</h2>
              <p className="mt-2 text-[14px] text-slate-500">Sign in to your account to continue</p>
            </div>

            {params.get('expired') && <Alert tone="warn" className="mb-6">{t('login.expired')}</Alert>}

            <form onSubmit={submit} className="space-y-5">
              <div className="space-y-1">
                <Label htmlFor="email">{t('login.email')}</Label>
                <Input id="email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} className="h-11" placeholder="name@company.com" />
              </div>
              <div className="space-y-1">
                <div className="flex items-center justify-between">
                  <Label htmlFor="password" className="mb-0">{t('login.password')}</Label>
                </div>
                <Input id="password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} className="h-11" placeholder="••••••••" />
              </div>
              {error && <Alert tone="error">{error}</Alert>}
              <Button type="submit" className="h-11 w-full text-[14px] mt-2" loading={busy === 'form'}>
                {t('login.submit')} <ArrowRight className="ml-2 h-4 w-4 opacity-70" />
              </Button>
            </form>

            {demo.data?.enabled && demo.data.accounts.length > 0 && (
              <div className="mt-12 border-t border-slate-200/60 pt-10">
                <div className="mb-6">
                  <h3 className="text-[14px] font-semibold text-slate-900">{t('login.demoTitle')}</h3>
                  <p className="mt-1 text-[13px] text-slate-500">{t('login.demoSubtitle')}</p>
                </div>
                <div className="grid gap-3">
                  {demo.data.accounts.map((a) => (
                    <button
                      key={a.key}
                      onClick={() => quick(a.key)}
                      disabled={!!busy}
                      className="group relative flex w-full items-center gap-4 rounded-xl border border-slate-200/80 bg-white p-3 text-left shadow-[0_1px_2px_rgba(0,0,0,0.02)] transition-all duration-200 hover:border-slate-300 hover:shadow-md disabled:opacity-50"
                    >
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-50 text-[13px] font-bold text-slate-700 ring-1 ring-slate-200/80 group-hover:bg-slate-100 transition-colors">
                        {a.name.split(' ').map((p) => p[0]).slice(0, 2).join('')}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="truncate text-[14px] font-semibold text-slate-900">{a.name}</span>
                          <span className={cn('rounded-[4px] px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider ring-1 ring-inset', ROLE_TONE[a.role])}>{t(`roles.${a.role}`)}</span>
                        </div>
                        <span className="mt-0.5 block truncate text-[12px] font-medium text-slate-500">{a.description}</span>
                      </div>
                      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-50 text-slate-400 opacity-0 transition-all duration-200 group-hover:opacity-100 group-hover:text-slate-900">
                        <ArrowRight className="h-4 w-4" aria-hidden />
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
