import * as React from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, ExternalLink, ShieldCheck, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { leaveApi } from '../../api/endpoints'
import type { Channel } from '../../api/client'
import type { ProposedAction } from '../../api/types'
import { useDebounced } from '../../hooks/useNow'
import { errorMessage } from '../../lib/errors'
import { fmtRange } from '../../lib/dates'
import { uuid } from '../../lib/utils'
import { Alert, Button, Input, Label, Select, Textarea } from '../ui/primitives'
import { PreviewPanel } from '../leave/PreviewPanel'
import { StatusBadge } from '../leave/StatusBadge'

function useInvalidateAll() {
  const qc = useQueryClient()
  return () => ['me', 'manager', 'hr', 'notifications', 'request'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }))
}

/**
 * Human confirmation step. Every field is editable; confirming calls the SAME endpoint as the Apply form
 * (POST /leave-requests), so balance, overlap, conflict, state-machine and audit rules all apply.
 */
export function SubmitProposalCard({ action, channel, onDone }: { action: ProposedAction; channel: Channel; onDone: (msg: string) => void }) {
  const { t } = useTranslation()
  const invalidate = useInvalidateAll()
  const types = useQuery({ queryKey: ['leave-types'], queryFn: leaveApi.types, staleTime: Infinity })
  const [form, setForm] = React.useState({
    leaveTypeCode: String(action.payload.leaveTypeCode ?? ''),
    startDate: String(action.payload.startDate ?? ''),
    endDate: String(action.payload.endDate ?? ''),
    reason: (action.payload.reason as string | null) ?? '',
  })
  const [done, setDone] = React.useState<number | null>(null)
  const clientRequestId = React.useRef(uuid())
  const d = useDebounced(form, 350)
  const edited = d.leaveTypeCode !== action.payload.leaveTypeCode || d.startDate !== action.payload.startDate || d.endDate !== action.payload.endDate
  const preview = useQuery({
    queryKey: ['preview', d.leaveTypeCode, d.startDate, d.endDate],
    queryFn: ({ signal }) => leaveApi.preview({ leaveTypeCode: d.leaveTypeCode, startDate: d.startDate, endDate: d.endDate }, signal),
    enabled: edited && !!d.startDate && !!d.endDate,
  })
  const pv = edited ? preview.data : action.preview ?? undefined
  const submit = useMutation({
    mutationFn: () => leaveApi.submit({ ...form, reason: form.reason || undefined, clientRequestId: clientRequestId.current }, channel),
    onSuccess: (r) => {
      setDone(r.id)
      invalidate()
      onDone(t('apply.submitted', { id: r.id, name: r.managerApprover?.name ?? 'HR' }))
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  if (done) {
    return (
      <Alert tone="success" icon={<CheckCircle2 className="h-4 w-4" />}>
        <Link to={`/requests/${done}`} className="font-medium underline">#{done}</Link> · {t('status.PENDING_MANAGER')}
      </Alert>
    )
  }
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }))
  return (
    <div className="space-y-3 rounded-xl border border-brand-200 bg-brand-50/40 p-3">
      <p className="text-xs font-medium text-brand-800">{t('assistant.understood')}</p>
      <div className="grid grid-cols-2 gap-2">
        <div className="col-span-2">
          <Label className="text-xs">{t('apply.leaveType')}</Label>
          <Select value={form.leaveTypeCode} onChange={set('leaveTypeCode')}>
            {types.data?.map((ty) => <option key={ty.code} value={ty.code}>{t(`leaveType.${ty.code}`, ty.displayName)}</option>)}
          </Select>
        </div>
        <div>
          <Label className="text-xs">{t('apply.startDate')}</Label>
          <Input type="date" value={form.startDate} onChange={set('startDate')} />
        </div>
        <div>
          <Label className="text-xs">{t('apply.endDate')}</Label>
          <Input type="date" value={form.endDate} min={form.startDate} onChange={set('endDate')} />
        </div>
        <div className="col-span-2">
          <Label className="text-xs">{t('apply.reason')}</Label>
          <Textarea value={form.reason} onChange={set('reason')} className="min-h-[52px]" maxLength={500} />
        </div>
      </div>
      <PreviewPanel preview={pv} loading={preview.isFetching} compact />
      <div className="flex flex-wrap gap-2">
        <Button size="sm" loading={submit.isPending} disabled={!pv || !pv.valid} onClick={() => submit.mutate()}>
          {t('assistant.confirmSubmit')}
        </Button>
        <Link to="/me/apply" state={{ ...form, fromAssistant: true }}>
          <Button size="sm" variant="outline"><ExternalLink className="h-3.5 w-3.5" aria-hidden />{t('assistant.openForm')}</Button>
        </Link>
      </div>
      <p className="text-[11px] text-slate-500">{t('assistant.notSubmitted')}</p>
    </div>
  )
}

export function CancelProposalCard({ action, channel, onDone }: { action: ProposedAction; channel: Channel; onDone: (msg: string) => void }) {
  const { t } = useTranslation()
  const invalidate = useInvalidateAll()
  const r = action.request!
  const [done, setDone] = React.useState(false)
  const m = useMutation({
    mutationFn: () => leaveApi.cancel(r.id, undefined, channel),
    onSuccess: () => { setDone(true); invalidate(); onDone(t('decision.cancelled')) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <div className="space-y-2 rounded-xl border border-slate-200 bg-white p-3">
      <div className="flex items-center justify-between gap-2">
        <Link to={`/requests/${r.id}`} className="text-sm font-medium hover:underline">#{r.id} · {fmtRange(r.startDate, r.endDate)}</Link>
        <StatusBadge status={done ? 'CANCELLED' : r.status} />
      </div>
      {!done && <Button size="sm" variant="danger" loading={m.isPending} onClick={() => m.mutate()}>{t('assistant.confirmCancel')}</Button>}
    </div>
  )
}

export function DecisionProposalCard({ action, channel, onDone }: { action: ProposedAction; channel: Channel; onDone: (msg: string) => void }) {
  const { t } = useTranslation()
  const invalidate = useInvalidateAll()
  const r = action.request!
  const approve = action.payload.decision === 'APPROVE'
  const flagged = !!action.conflict?.flagged
  const [ack, setAck] = React.useState(false)
  const [comment, setComment] = React.useState(String(action.payload.comment ?? ''))
  const [done, setDone] = React.useState(false)
  const m = useMutation({
    mutationFn: () => leaveApi.decide(r.id, {
      stage: action.payload.stage, decision: action.payload.decision, comment: comment || undefined, acknowledgeConflict: ack,
    }, channel),
    onSuccess: (d) => { setDone(true); invalidate(); onDone(`#${d.id} → ${t(`status.${d.status}`)}`) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <div className="space-y-2 rounded-xl border border-slate-200 bg-white p-3">
      <div className="flex items-center justify-between gap-2">
        <Link to={`/requests/${r.id}`} className="text-sm font-medium hover:underline">#{r.id} · {r.employee.name}</Link>
        <span className="inline-flex items-center gap-1 text-xs text-slate-500"><ShieldCheck className="h-3.5 w-3.5" />{action.payload.stage}</span>
      </div>
      <p className="text-xs text-slate-600">{fmtRange(r.startDate, r.endDate)} · {t(`leaveType.${r.leaveTypeCode}`)}</p>
      {!done && <>
        {approve && flagged && (
          <label className="flex items-start gap-2 rounded-lg bg-amber-50 p-2 text-xs text-amber-900">
            <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} className="mt-0.5" />
            <span><TriangleAlert className="mr-1 inline h-3.5 w-3.5" />{t('decision.ack')}</span>
          </label>
        )}
        {!approve && <Textarea value={comment} onChange={(e) => setComment(e.target.value)} className="min-h-[52px]" maxLength={1000} />}
        <Button size="sm" variant={approve ? 'success' : 'danger'} loading={m.isPending}
          disabled={(approve && flagged && !ack) || (!approve && comment.trim().length < 3)} onClick={() => m.mutate()}>
          {approve ? t('assistant.confirmApprove') : t('assistant.confirmReject')}
        </Button>
      </>}
      {done && <p className="text-xs font-medium text-emerald-700">✓</p>}
    </div>
  )
}
