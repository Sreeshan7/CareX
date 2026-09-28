import { Bot, Check, Clock, MessageSquare, Mic, Send, X, Ban, TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { TimelineEntry } from '../../api/types'
import { fmtDateTime } from '../../lib/dates'
import { cn } from '../../lib/utils'

const ICON = { SUBMIT: Send, APPROVE: Check, REJECT: X, ESCALATE: Clock, CANCEL: Ban }
const TONE = {
  SUBMIT: 'bg-slate-100 text-slate-600', APPROVE: 'bg-emerald-100 text-emerald-700', REJECT: 'bg-rose-100 text-rose-700',
  ESCALATE: 'bg-red-100 text-red-700', CANCEL: 'bg-slate-100 text-slate-500',
}

export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  const { t } = useTranslation()
  return (
    <ol className="relative space-y-5 border-l border-slate-200 pl-6">
      {entries.map((e) => {
        const Icon = ICON[e.action]
        return (
          <li key={e.id} className="relative">
            <span className={cn('absolute -left-[37px] flex h-6 w-6 items-center justify-center rounded-full ring-4 ring-white', TONE[e.action])}>
              <Icon className="h-3.5 w-3.5" aria-hidden />
            </span>
            <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
              <p className="text-sm font-medium text-slate-900">
                {t(`actions.${e.action}`)}
                {e.stage !== 'NONE' && <span className="font-normal text-slate-500"> · {t(`stage.${e.stage.toLowerCase()}`)}</span>}
              </p>
              <p className="text-sm text-slate-600">
                {e.actor ? e.actor.name : <span className="inline-flex items-center gap-1"><Bot className="h-3.5 w-3.5" aria-hidden />{t('common.system')}</span>}
                {e.actorCapacity !== 'OWNER' && e.actorCapacity !== 'SYSTEM' && (
                  <span className="ml-1 text-xs text-slate-400">({e.actorCapacity.replace(/_/g, ' ').toLowerCase()})</span>
                )}
              </p>
              {e.channel === 'VOICE' && <span className="inline-flex items-center gap-0.5 text-xs text-brand-700"><Mic className="h-3 w-3" aria-hidden />{t('common.voice')}</span>}
              {e.channel === 'CHAT' && <span className="inline-flex items-center gap-0.5 text-xs text-brand-700"><MessageSquare className="h-3 w-3" aria-hidden />{t('common.chat')}</span>}
            </div>
            <p className="text-xs text-slate-400">{fmtDateTime(e.at)}</p>
            {e.comment && <p className="mt-1.5 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">“{e.comment}”</p>}
            {e.conflictAcknowledged && (
              <p className="mt-1 inline-flex items-center gap-1 text-xs text-orange-700"><TriangleAlert className="h-3 w-3" aria-hidden />Conflict acknowledged</p>
            )}
          </li>
        )
      })}
    </ol>
  )
}
