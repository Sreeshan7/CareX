import * as React from 'react'
import { AlertTriangle, Inbox, Loader2, RefreshCw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '../../lib/utils'
import { errorMessage } from '../../lib/errors'

// ---------------------------------------------------------------- Button
type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success' | 'outline'
type Size = 'sm' | 'md' | 'lg' | 'icon'

const variants: Record<Variant, string> = {
  primary: 'bg-slate-900 text-white hover:bg-slate-800 shadow-[0_1px_2px_rgba(0,0,0,0.12)] border border-slate-900/10 active:scale-[0.98]',
  secondary: 'bg-white text-slate-700 hover:bg-slate-50 shadow-sm border border-slate-200/80 active:scale-[0.98]',
  outline: 'border border-slate-200/80 bg-transparent text-slate-700 hover:bg-slate-50 active:scale-[0.98]',
  ghost: 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80 active:scale-[0.98]',
  danger: 'bg-white text-rose-600 hover:bg-rose-50 shadow-sm border border-rose-200/80 active:scale-[0.98]',
  success: 'bg-emerald-600 text-white hover:bg-emerald-700 shadow-[0_1px_2px_rgba(0,0,0,0.12)] border border-emerald-600/10 active:scale-[0.98]',
}
const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-[13px] gap-1.5',
  md: 'h-9 px-4 text-[13px] gap-2',
  lg: 'h-11 px-6 text-sm gap-2',
  icon: 'h-9 w-9 justify-center',
}

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', loading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={cn('inline-flex items-center justify-center rounded-[8px] font-medium transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-500/50 focus-visible:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50',
        variants[variant], sizes[size], className)}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
      {children}
    </button>
  ),
)
Button.displayName = 'Button'

// ---------------------------------------------------------------- Card
export function Card({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-[12px] bg-white ring-1 ring-slate-200/60 shadow-sm', className)} {...props} />
}
export function CardHeader({ title, subtitle, action, className }: { title: React.ReactNode; subtitle?: React.ReactNode; action?: React.ReactNode; className?: string }) {
  return (
    <div className={cn('flex items-start justify-between gap-4 border-b border-slate-100/80 px-6 py-5', className)}>
      <div className="min-w-0">
        <h2 className="text-[15px] font-semibold tracking-tight text-slate-900">{title}</h2>
        {subtitle && <p className="mt-1 text-[13px] text-slate-500">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}

// ---------------------------------------------------------------- Badge
export function Badge({ className, children, ...props }: React.HTMLAttributes<HTMLSpanElement>) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] px-2 py-1 text-[11px] font-semibold uppercase tracking-wider ring-1 ring-inset', className)} {...props}>
      {children}
    </span>
  )
}

// ---------------------------------------------------------------- Form controls
export function Label({ className, ...props }: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn('mb-1.5 block text-[13px] font-medium text-slate-700', className)} {...props} />
}

const controlBase = 'block w-full rounded-[8px] border-0 ring-1 ring-inset ring-slate-200/80 bg-white px-3 text-[13px] text-slate-900 shadow-[0_1px_2px_rgba(0,0,0,0.02)] placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-accent-500 focus:bg-white hover:ring-slate-300 transition-all duration-200 disabled:bg-slate-50 disabled:ring-slate-200 disabled:text-slate-500'

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => <input ref={ref} className={cn(controlBase, 'h-9', className)} {...props} />,
)
Input.displayName = 'Input'

export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, ...props }, ref) => <select ref={ref} className={cn(controlBase, 'h-9 py-0 pr-8', className)} {...props} />,
)
Select.displayName = 'Select'

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => <textarea ref={ref} className={cn(controlBase, 'min-h-[80px] py-2', className)} {...props} />,
)
Textarea.displayName = 'Textarea'

export function FieldError({ children }: { children?: React.ReactNode }) {
  if (!children) return null
  return <p className="mt-1 text-xs text-rose-600" role="alert">{children}</p>
}

// ---------------------------------------------------------------- Feedback
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-lg bg-slate-200/70', className)} aria-hidden />
}

export function Spinner({ label }: { label?: string }) {
  const { t } = useTranslation()
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500" role="status">
      <Loader2 className="h-5 w-5 animate-spin" aria-hidden /> {label ?? t('common.loading')}
    </div>
  )
}

export function EmptyState({ title, children, icon }: { title: string; children?: React.ReactNode; icon?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-16 text-center animate-fadeIn">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-[10px] bg-slate-100 ring-1 ring-slate-200/50 text-slate-400 shadow-sm">{icon ?? <Inbox className="h-5 w-5" aria-hidden />}</div>
      <p className="text-[15px] font-semibold text-slate-900">{title}</p>
      {children && <div className="mt-2 max-w-sm text-[13px] text-slate-500">{children}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center animate-fadeIn" role="alert">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-[10px] bg-rose-50 ring-1 ring-rose-100 text-rose-500 shadow-sm"><AlertTriangle className="h-5 w-5" aria-hidden /></div>
      <p className="max-w-md text-[13px] font-medium text-slate-700">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-5" onClick={onRetry}>
          <RefreshCw className="h-3.5 w-3.5" aria-hidden /> {t('common.retry')}
        </Button>
      )}
    </div>
  )
}

export function Alert({ tone = 'info', children, className, icon }: { tone?: 'info' | 'warn' | 'error' | 'success'; children: React.ReactNode; className?: string; icon?: React.ReactNode }) {
  const tones = {
    info: 'bg-blue-50/50 text-blue-900 ring-blue-200/50',
    warn: 'bg-amber-50/50 text-amber-900 ring-amber-200/50',
    error: 'bg-rose-50/50 text-rose-900 ring-rose-200/50',
    success: 'bg-emerald-50/50 text-emerald-900 ring-emerald-200/50',
  }
  return (
    <div className={cn('flex gap-3 rounded-[10px] px-4 py-3.5 text-[13px] ring-1 ring-inset', tones[tone], className)} role={tone === 'error' ? 'alert' : 'status'}>
      {icon && <span className="mt-0.5 shrink-0">{icon}</span>}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
}

export function StatCard({ label, value, hint, icon, tone = 'slate' }: { label: string; value: React.ReactNode; hint?: React.ReactNode; icon?: React.ReactNode; tone?: 'slate' | 'amber' | 'red' | 'brand' | 'sky' | 'emerald' }) {
  const tones = {
    slate: 'bg-slate-100 text-slate-600 ring-slate-200/50', amber: 'bg-amber-100 text-amber-700 ring-amber-200/50', red: 'bg-red-100 text-red-700 ring-red-200/50',
    brand: 'bg-slate-900 text-white ring-slate-800', sky: 'bg-sky-100 text-sky-700 ring-sky-200/50', emerald: 'bg-emerald-100 text-emerald-700 ring-emerald-200/50',
  }
  return (
    <Card className="p-5 transition-all duration-300 hover:shadow-md">
      <div className="flex items-center justify-between">
        <p className="text-[13px] font-medium text-slate-500">{label}</p>
        {icon && <span className={cn('flex h-8 w-8 items-center justify-center rounded-[8px] ring-1 ring-inset shadow-sm', tones[tone])}>{icon}</span>}
      </div>
      <p className="mt-3 text-3xl font-semibold tracking-tight text-slate-900">{value}</p>
      {hint && <p className="mt-2 text-[12px] font-medium text-slate-400">{hint}</p>}
    </Card>
  )
}

export function PageHeader({ title, subtitle, action }: { title: React.ReactNode; subtitle?: React.ReactNode; action?: React.ReactNode }) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between animate-fadeIn">
      <div>
        <h1 className="text-[28px] font-bold tracking-tight text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1.5 text-[14px] text-slate-500">{subtitle}</p>}
      </div>
      {action && <div className="flex flex-wrap gap-2">{action}</div>}
    </div>
  )
}
