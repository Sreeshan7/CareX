import { Check, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { LeaveRequestDetail } from '../../api/types'
import { cn } from '../../lib/utils'

type StepState = 'done' | 'current' | 'escalated' | 'todo' | 'failed' | 'skipped'

/** Submitted → Manager → HR → Approved, with the current step pulsing and escalated steps red. */
export function StageStepper({ request }: { request: Pick<LeaveRequestDetail, 'status' | 'timeline'> }) {
  const { t } = useTranslation()
  const s = request.status
  const managerDone = request.timeline.some((e) => e.stage === 'MANAGER' && e.action === 'APPROVE')
  const hrDone = s === 'APPROVED' || request.timeline.some((e) => e.stage === 'HR' && e.action === 'APPROVE')
  const rejectedAt = request.timeline.find((e) => e.action === 'REJECT')?.stage
  const cancelled = s === 'CANCELLED'

  const mgr: StepState = managerDone ? 'done' : rejectedAt === 'MANAGER' ? 'failed'
    : s === 'MANAGER_ESCALATED' ? 'escalated' : s === 'PENDING_MANAGER' ? 'current' : cancelled ? 'skipped' : 'todo'
  const hr: StepState = hrDone ? 'done' : rejectedAt === 'HR' ? 'failed'
    : s === 'HR_ESCALATED' ? 'escalated' : s === 'PENDING_HR' ? 'current' : 'todo'
  const final: StepState = s === 'APPROVED' ? 'done' : s === 'REJECTED' || cancelled ? 'failed' : 'todo'
  const finalLabel = s === 'REJECTED' ? t('stage.rejected') : cancelled ? t('stage.cancelled') : t('stage.approved')

  const steps: { label: string; state: StepState }[] = [
    { label: t('stage.submitted'), state: 'done' },
    { label: t('stage.manager'), state: mgr },
    { label: t('stage.hr'), state: hr === 'todo' && (cancelled || rejectedAt === 'MANAGER') ? 'skipped' : hr },
    { label: finalLabel, state: final },
  ]

  return (
    <ol className="flex items-center" aria-label="Approval progress">
      {steps.map((st, i) => (
        <li key={i} className={cn('flex items-center', i < steps.length - 1 && 'flex-1')}>
          <div className="flex flex-col items-center gap-1.5">
            <span
              className={cn('flex h-8 w-8 items-center justify-center rounded-full border-2 text-xs font-semibold',
                st.state === 'done' && 'border-emerald-500 bg-emerald-500 text-white',
                st.state === 'current' && 'animate-pulseRing border-brand-600 bg-white text-brand-700',
                st.state === 'escalated' && 'animate-pulseRing border-red-500 bg-red-50 text-red-600',
                st.state === 'failed' && 'border-rose-500 bg-rose-500 text-white',
                (st.state === 'todo' || st.state === 'skipped') && 'border-slate-300 bg-white text-slate-400')}
              aria-current={st.state === 'current' || st.state === 'escalated' ? 'step' : undefined}
            >
              {st.state === 'done' ? <Check className="h-4 w-4" /> : st.state === 'failed' ? <X className="h-4 w-4" /> : i + 1}
            </span>
            <span className={cn('whitespace-nowrap text-xs font-medium', st.state === 'escalated' ? 'text-red-600' : 'text-slate-600')}>
              {st.label}
            </span>
          </div>
          {i < steps.length - 1 && (
            <div className={cn('mx-2 mb-5 h-0.5 flex-1 rounded', steps[i + 1].state === 'done' || steps[i + 1].state === 'current' || steps[i + 1].state === 'escalated' || (steps[i + 1].state === 'failed' && i + 1 < 3) ? 'bg-emerald-400' : 'bg-slate-200')} />
          )}
        </li>
      ))}
    </ol>
  )
}
