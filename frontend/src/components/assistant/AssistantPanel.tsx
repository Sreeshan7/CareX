import * as React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Bot, Loader2, Mic, Send, Sparkles, Square, Volume2, VolumeX, X } from 'lucide-react'
import { assistantApi } from '../../api/endpoints'
import type { Channel } from '../../api/client'
import type { AssistantReply, BalanceView, CanonicalCommand, LeaveRequestSummary } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import { useVoiceRecorder } from '../../hooks/useVoiceRecorder'
import { errorMessage } from '../../lib/errors'
import { fmtRange } from '../../lib/dates'
import { cn, formatDays, safeLocalStorage } from '../../lib/utils'
import { Sheet } from '../ui/dialog'
import { StatusBadge } from '../leave/StatusBadge'
import { CancelProposalCard, DecisionProposalCard, SubmitProposalCard } from './ProposalCards'

interface Msg { id: number; role: 'user' | 'assistant' | 'system'; text: string; reply?: AssistantReply; channel?: Channel }

export function AssistantLauncher() {
  const { t } = useTranslation()
  const [open, setOpen] = React.useState(false)
  return (
    <>
      {!open && (
        <button
          onClick={() => setOpen(true)}
          className="fixed bottom-5 right-5 z-30 flex items-center gap-2 rounded-full bg-brand-700 px-4 py-3 text-sm font-semibold text-white shadow-lg transition hover:bg-brand-800"
          aria-label={t('assistant.title')}
        >
          <Sparkles className="h-5 w-5" aria-hidden /><span className="hidden sm:inline">{t('assistant.title')}</span>
        </button>
      )}
      <Sheet open={open} onOpenChange={setOpen} title={t('assistant.title')}>
        {open && <AssistantPanel onClose={() => setOpen(false)} />}
      </Sheet>
    </>
  )
}

function AssistantPanel({ onClose }: { onClose: () => void }) {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const status = useQuery({ queryKey: ['assistant', 'status'], queryFn: assistantApi.status, staleTime: 60_000 })
  const defaultLang = user?.preferredLanguage ?? (i18n.language.startsWith('hi') ? 'hi-IN' : i18n.language.startsWith('ta') ? 'ta-IN' : 'en-IN')
  const [lang, setLang] = React.useState(() => safeLocalStorage('carex.assistantLang') ?? defaultLang)
  const [speak, setSpeak] = React.useState(() => safeLocalStorage('carex.tts') === '1')
  const [text, setText] = React.useState('')
  const [fromVoice, setFromVoice] = React.useState(false)
  const [busy, setBusy] = React.useState<null | 'transcribing' | 'thinking'>(null)
  const [draft, setDraft] = React.useState<CanonicalCommand | null>(null)
  const extra = user?.role === 'MANAGER' ? t('assistant.welcomeManager') : user?.role === 'HR' ? t('assistant.welcomeHr') : ''
  const [msgs, setMsgs] = React.useState<Msg[]>([{ id: 0, role: 'assistant', text: t('assistant.welcome', { extra }) }])
  const listRef = React.useRef<HTMLDivElement>(null)
  const inputRef = React.useRef<HTMLTextAreaElement>(null)
  const nextId = React.useRef(1)
  const s = status.data

  React.useEffect(() => { listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' }) }, [msgs, busy])
  React.useEffect(() => { safeLocalStorage('carex.assistantLang', lang) }, [lang])
  React.useEffect(() => { safeLocalStorage('carex.tts', speak ? '1' : '0') }, [speak])

  const push = (m: Omit<Msg, 'id'>) => setMsgs((xs) => [...xs, { ...m, id: nextId.current++ }])

  const playTts = async (reply: AssistantReply) => {
    if (!speak || !s?.ttsAvailable) return
    try {
      const blob = await assistantApi.speak(reply.replyText.slice(0, 900), reply.replyLanguage)
      const url = URL.createObjectURL(blob)
      const audio = new Audio(url)
      audio.onended = () => URL.revokeObjectURL(url)
      await audio.play()
    } catch { /* TTS is optional: text is always shown */ }
  }

  const send = async (value: string, channelOverride?: Channel, display?: string) => {
    const content = value.trim()
    if (!content || busy) return
    const channel: Channel = channelOverride ?? (fromVoice ? 'VOICE' : 'CHAT')
    push({ role: 'user', text: display ?? content, channel })
    setText('')
    setFromVoice(false)
    setBusy('thinking')
    try {
      const reply = await assistantApi.message(content, lang, draft, channel)
      setDraft(reply.command.intent !== 'UNKNOWN' && reply.command.status !== 'ANSWERED' && reply.command.status !== 'POLICY_DENIED' ? reply.command : null)
      push({ role: 'assistant', text: reply.replyText, reply, channel })
      void playTts(reply)
    } catch (e) {
      push({ role: 'system', text: errorMessage(e) })
    } finally {
      setBusy(null)
    }
  }

  const onAudio = React.useCallback(async (blob: Blob) => {
    setBusy('transcribing')
    try {
      const res = await assistantApi.transcribe(blob, lang)
      setText(res.transcript)
      setFromVoice(true)
      if (res.languageCode && res.languageCode !== lang && /^[a-z]{2,3}-IN$/.test(res.languageCode)) setLang(res.languageCode)
      setTimeout(() => inputRef.current?.focus(), 50)
    } catch {
      push({ role: 'system', text: t('assistant.sttFailed') })
      inputRef.current?.focus()
    } finally {
      setBusy(null)
    }
  }, [lang, t])
  const recorder = useVoiceRecorder(onAudio)

  const degraded = s && (!s.llmAvailable || !s.sttAvailable)
  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 border-b border-slate-200 px-4 py-3">
        <span className="rounded-lg bg-brand-100 p-1.5 text-brand-700"><Bot className="h-5 w-5" aria-hidden /></span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold">{t('assistant.title')}</p>
          <p className="truncate text-xs text-slate-500">{s ? (s.provider === 'sarvam' ? 'Sarvam AI' : 'Offline mode') : '…'}</p>
        </div>
        <select value={lang} onChange={(e) => setLang(e.target.value)} className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs" aria-label={t('common.language')}>
          {(s?.languages ?? [{ code: 'en-IN', name: 'English' }]).map((l) => <option key={l.code} value={l.code}>{l.name}</option>)}
        </select>
        {s?.ttsAvailable && (
          <button onClick={() => setSpeak((v) => !v)} className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" aria-pressed={speak} aria-label={t('assistant.speak')} title={t('assistant.speak')}>
            {speak ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
          </button>
        )}
        <button onClick={onClose} className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" aria-label={t('common.close')}><X className="h-5 w-5" /></button>
      </div>
      {degraded && <p className="bg-amber-50 px-4 py-2 text-xs text-amber-800" role="status">{t('assistant.degraded')}</p>}

      <div ref={listRef} className="flex-1 space-y-3 overflow-y-auto px-4 py-4" aria-live="polite">
        {msgs.map((m) => <Message key={m.id} m={m} onOption={(v, label) => send(v, undefined, label)} onDone={(txt) => push({ role: 'system', text: txt })} />)}
        {busy && (
          <p className="flex items-center gap-2 text-xs text-slate-500" role="status">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />{busy === 'transcribing' ? t('assistant.transcribing') : t('assistant.thinking')}
          </p>
        )}
        {msgs.length === 1 && (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {[t('assistant.sugBalance'), t('assistant.sugApply'), t('assistant.sugRequests'),
              ...(user?.role !== 'EMPLOYEE' ? [t('assistant.sugPending'), t('assistant.sugEscalations')] : [])].map((sug) => (
              <button key={sug} onClick={() => send(sug)} className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-700 hover:border-brand-300 hover:bg-brand-50">{sug}</button>
            ))}
          </div>
        )}
      </div>

      <form className="border-t border-slate-200 p-3" onSubmit={(e) => { e.preventDefault(); void send(text) }}>
        {fromVoice && text && <p className="mb-1.5 text-xs font-medium text-brand-700">{t('assistant.editTranscript')}</p>}
        {recorder.state === 'recording' && (
          <p className="mb-1.5 flex items-center gap-2 text-xs font-medium text-red-600" role="status">
            <span className="h-2 w-2 animate-pulse rounded-full bg-red-600" />{t('assistant.listening')} · {recorder.elapsed}s / 29s
          </p>
        )}
        {recorder.state === 'denied' && <p className="mb-1.5 text-xs text-amber-700">{t('assistant.micDenied')}</p>}
        <div className="flex items-end gap-2">
          <textarea
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void send(text) } }}
            placeholder={t('assistant.placeholder')}
            rows={2}
            maxLength={500}
            className="min-h-[44px] flex-1 resize-none rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30"
          />
          {s?.sttAvailable && recorder.state !== 'unsupported' && (
            <button type="button"
              onClick={() => (recorder.state === 'recording' ? recorder.stop() : recorder.start())}
              disabled={busy !== null}
              className={cn('flex h-11 w-11 items-center justify-center rounded-xl text-white transition disabled:opacity-50',
                recorder.state === 'recording' ? 'animate-pulseRing bg-red-600' : 'bg-slate-800 hover:bg-slate-700')}
              aria-pressed={recorder.state === 'recording'}
              aria-label={recorder.state === 'recording' ? t('assistant.micStop') : t('assistant.mic')}
            >
              {recorder.state === 'recording' ? <Square className="h-4 w-4" /> : <Mic className="h-5 w-5" />}
            </button>
          )}
          <button type="submit" disabled={!text.trim() || busy !== null}
            className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-700 text-white hover:bg-brand-800 disabled:opacity-50" aria-label={t('assistant.send')}>
            <Send className="h-4 w-4" />
          </button>
        </div>
      </form>
    </div>
  )
}

function Message({ m, onOption, onDone }: { m: Msg; onOption: (v: string, label: string) => void; onDone: (text: string) => void }) {
  const { t } = useTranslation()
  if (m.role === 'user') {
    return (
      <div className="flex justify-end">
        <div className="max-w-[85%] rounded-2xl rounded-br-sm bg-brand-700 px-3.5 py-2 text-sm text-white">
          {m.channel === 'VOICE' && <Mic className="mr-1 inline h-3.5 w-3.5 opacity-80" aria-label={t('common.voice')} />}{m.text}
        </div>
      </div>
    )
  }
  if (m.role === 'system') {
    return <p className="rounded-lg bg-slate-100 px-3 py-2 text-center text-xs text-slate-600">{m.text}</p>
  }
  const r = m.reply
  const channel: Channel = m.channel === 'VOICE' ? 'VOICE' : 'CHAT'
  return (
    <div className="max-w-[92%] space-y-2">
      <div className="rounded-2xl rounded-bl-sm bg-slate-100 px-3.5 py-2 text-sm text-slate-800">
        {m.text}
        {r && r.replyLanguage !== 'en-IN' && r.replyTextEnglish !== r.replyText && (
          <details className="mt-1 text-xs text-slate-500"><summary className="cursor-pointer">English</summary>{r.replyTextEnglish}</details>
        )}
        {r?.degraded && r.degradedReason && <p className="mt-1 text-[11px] text-amber-700">⚠ {r.degradedReason}</p>}
      </div>
      {r?.clarification && r.clarification.options.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {r.clarification.options.map((o) => (
            <button key={o.value} onClick={() => onOption(o.value, o.label)}
              className="rounded-full border border-brand-200 bg-white px-3 py-1 text-xs font-medium text-brand-800 hover:bg-brand-50">{o.label}</button>
          ))}
        </div>
      )}
      {r?.proposedAction?.type === 'SUBMIT_LEAVE' && <SubmitProposalCard action={r.proposedAction} channel={channel} onDone={onDone} />}
      {r?.proposedAction?.type === 'CANCEL_LEAVE' && <CancelProposalCard action={r.proposedAction} channel={channel} onDone={onDone} />}
      {r?.proposedAction?.type === 'DECIDE_REQUEST' && <DecisionProposalCard action={r.proposedAction} channel={channel} onDone={onDone} />}
      {r?.cards.map((c, i) => <DataCard key={i} kind={c.kind} data={c.data} />)}
    </div>
  )
}

function DataCard({ kind, data }: { kind: string; data: any }) {
  const { t } = useTranslation()
  if (kind === 'BALANCES') {
    return (
      <div className="grid grid-cols-3 gap-1.5">
        {(data as BalanceView[]).map((b) => (
          <div key={b.leaveTypeCode} className="rounded-lg border border-slate-200 bg-white p-2 text-center">
            <p className="text-[11px] text-slate-500">{t(`leaveType.${b.leaveTypeCode}`)}</p>
            <p className="text-lg font-semibold">{formatDays(b.available)}</p>
            <p className="text-[10px] text-slate-400">{formatDays(b.pending)} {t('employee.pending')}</p>
          </div>
        ))}
      </div>
    )
  }
  if (kind === 'REQUESTS') {
    return (
      <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
        {(data as LeaveRequestSummary[]).slice(0, 6).map((r) => (
          <li key={r.id}>
            <Link to={`/requests/${r.id}`} className="flex items-center justify-between gap-2 px-2.5 py-2 text-xs hover:bg-slate-50">
              <span className="min-w-0 truncate"><b>#{r.id}</b> {r.employee.name} · {fmtRange(r.startDate, r.endDate)}</span>
              <StatusBadge status={r.status} />
            </Link>
          </li>
        ))}
      </ul>
    )
  }
  return null
}
