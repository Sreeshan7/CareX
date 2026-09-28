import * as React from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Check, TriangleAlert, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { leaveApi } from '../../api/endpoints'
import { ApiError, type Channel } from '../../api/client'
import type { AllowedAction } from '../../api/types'
import { errorMessage } from '../../lib/errors'
import { Alert, Button, Label, Textarea } from '../ui/primitives'
import { Modal } from '../ui/dialog'

interface Target { id: number; employeeName: string; flagged: boolean; range: string }

/** Buttons are rendered ONLY from server-computed allowedActions (implementation.md §14.3). */
export function RequestActions({ target, allowed, size = 'md', channel = 'WEB', onDone }: {
  target: Target
  allowed: AllowedAction[]
  size?: 'sm' | 'md'
  channel?: Channel
  onDone?: () => void
}) {
  const { t } = useTranslation()
  const [dialog, setDialog] = React.useState<null | { stage: 'MANAGER' | 'HR'; decision: 'APPROVE' | 'REJECT' } | 'cancel'>(null)
  if (allowed.length === 0) return null
  return (
    <div className="flex flex-wrap gap-2">
      {allowed.includes('MANAGER_APPROVE') && (
        <Button size={size} variant="success" onClick={() => setDialog({ stage: 'MANAGER', decision: 'APPROVE' })}>
          <Check className="h-4 w-4" aria-hidden />{t('actions.MANAGER_APPROVE')}
        </Button>
      )}
      {allowed.includes('HR_APPROVE') && (
        <Button size={size} variant="success" onClick={() => setDialog({ stage: 'HR', decision: 'APPROVE' })}>
          <Check className="h-4 w-4" aria-hidden />{t('actions.HR_APPROVE')}
        </Button>
      )}
      {allowed.includes('MANAGER_REJECT') && (
        <Button size={size} variant="outline" onClick={() => setDialog({ stage: 'MANAGER', decision: 'REJECT' })}>
          <X className="h-4 w-4" aria-hidden />{t('actions.MANAGER_REJECT')}
        </Button>
      )}
      {allowed.includes('HR_REJECT') && (
        <Button size={size} variant="outline" onClick={() => setDialog({ stage: 'HR', decision: 'REJECT' })}>
          <X className="h-4 w-4" aria-hidden />{t('actions.HR_REJECT')}
        </Button>
      )}
      {allowed.includes('CANCEL') && (
        <Button size={size} variant="outline" onClick={() => setDialog('cancel')}>{t('actions.CANCEL')}</Button>
      )}
      {dialog && dialog !== 'cancel' && (
        <DecisionDialog target={target} stage={dialog.stage} decision={dialog.decision} channel={channel}
          onClose={() => setDialog(null)} onDone={onDone} />
      )}
      {dialog === 'cancel' && <CancelDialog target={target} channel={channel} onClose={() => setDialog(null)} onDone={onDone} />}
    </div>
  )
}

function useInvalidate() {
  const qc = useQueryClient()
  return (id: number) => {
    qc.invalidateQueries({ queryKey: ['request', id] })
    qc.invalidateQueries({ queryKey: ['me'] })
    qc.invalidateQueries({ queryKey: ['manager'] })
    qc.invalidateQueries({ queryKey: ['hr'] })
    qc.invalidateQueries({ queryKey: ['notifications'] })
  }
}

export function DecisionDialog({ target, stage, decision, channel, onClose, onDone }: {
  target: Target; stage: 'MANAGER' | 'HR'; decision: 'APPROVE' | 'REJECT'; channel: Channel; onClose: () => void; onDone?: () => void
}) {
  const { t } = useTranslation()
  const invalidate = useInvalidate()
  const [comment, setComment] = React.useState('')
  const [ack, setAck] = React.useState(false)
  const approve = decision === 'APPROVE'
  const m = useMutation({
    mutationFn: () => leaveApi.decide(target.id, { stage, decision, comment: comment.trim() || undefined, acknowledgeConflict: ack }, channel),
    onSuccess: () => {
      toast.success(approve ? t('decision.approved') : t('decision.rejected'))
      invalidate(target.id)
      onClose()
      onDone?.()
    },
    onError: (e) => {
      toast.error(errorMessage(e))
      if (e instanceof ApiError && e.code === 'INVALID_TRANSITION') { invalidate(target.id); onClose() }
    },
  })
  const needsAck = approve && target.flagged
  const disabled = (needsAck && !ack) || (!approve && comment.trim().length < 3)
  return (
    <Modal open onOpenChange={(o) => !o && onClose()}
      title={approve ? t('decision.approveTitle', { id: target.id }) : t('decision.rejectTitle', { id: target.id })}
      description={`${target.employeeName} · ${target.range}`}>
      <div className="space-y-4">
        {approve && <p className="text-sm text-slate-600">{stage === 'MANAGER' ? t('decision.managerNote') : t('decision.hrNote')}</p>}
        {needsAck && (
          <Alert tone="warn" icon={<TriangleAlert className="h-4 w-4" />}>
            <p>{t('decision.ackRequired')}</p>
            <label className="mt-2 flex cursor-pointer items-start gap-2 font-medium">
              <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-amber-400 text-brand-600" checked={ack} onChange={(e) => setAck(e.target.checked)} />
              {t('decision.ack')}
            </label>
          </Alert>
        )}
        <div>
          <Label htmlFor="decision-comment">{t('common.comment')}</Label>
          <Textarea id="decision-comment" value={comment} onChange={(e) => setComment(e.target.value)} placeholder={t('decision.commentPlaceholder')} maxLength={1000} />
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>{t('common.close')}</Button>
          <Button variant={approve ? 'success' : 'danger'} loading={m.isPending} disabled={disabled} onClick={() => m.mutate()}>
            {approve ? t(stage === 'HR' ? 'actions.HR_APPROVE' : 'actions.MANAGER_APPROVE') : t('actions.MANAGER_REJECT')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

export function CancelDialog({ target, channel, onClose, onDone }: { target: Target; channel: Channel; onClose: () => void; onDone?: () => void }) {
  const { t } = useTranslation()
  const invalidate = useInvalidate()
  const [reason, setReason] = React.useState('')
  const m = useMutation({
    mutationFn: () => leaveApi.cancel(target.id, reason.trim() || undefined, channel),
    onSuccess: () => { toast.success(t('decision.cancelled')); invalidate(target.id); onClose(); onDone?.() },
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={t('decision.cancelTitle', { id: target.id })} description={target.range}>
      <p className="text-sm text-slate-600">{t('decision.cancelBody')}</p>
      <div className="mt-4">
        <Label htmlFor="cancel-reason">{t('common.reason')}</Label>
        <Textarea id="cancel-reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
      </div>
      <div className="mt-4 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>{t('decision.keep')}</Button>
        <Button variant="danger" loading={m.isPending} onClick={() => m.mutate()}>{t('actions.CANCEL')}</Button>
      </div>
    </Modal>
  )
}
