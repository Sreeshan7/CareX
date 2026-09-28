import * as React from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { leaveApi } from '../../api/endpoints'
import { Alert, Button, Card, CardHeader, FieldError, Input, Label, PageHeader, Select, Textarea } from '../../components/ui/primitives'
import { PreviewPanel } from '../../components/leave/PreviewPanel'
import { errorMessage } from '../../lib/errors'
import { ApiError } from '../../api/client'
import { isoToday } from '../../lib/dates'
import { uuid } from '../../lib/utils'
import { useDebounced } from '../../hooks/useNow'

export interface ApplyPrefill { leaveTypeCode?: string; startDate?: string; endDate?: string; reason?: string; fromAssistant?: boolean }

export function ApplyLeavePage() {
  const { t } = useTranslation()
  const nav = useNavigate()
  const qc = useQueryClient()
  const prefill = (useLocation().state ?? {}) as ApplyPrefill
  const types = useQuery({ queryKey: ['leave-types'], queryFn: leaveApi.types, staleTime: Infinity })
  const [form, setForm] = React.useState({
    leaveTypeCode: prefill.leaveTypeCode ?? '',
    startDate: prefill.startDate ?? '',
    endDate: prefill.endDate ?? '',
    reason: prefill.reason ?? '',
  })
  // Idempotency key: generated once per form instance and reused on retries (implementation.md §7.5)
  const clientRequestId = React.useRef(uuid())
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({})

  const debounced = useDebounced(form, 400)
  const canPreview = !!debounced.leaveTypeCode && !!debounced.startDate && !!debounced.endDate
  const preview = useQuery({
    queryKey: ['preview', debounced.leaveTypeCode, debounced.startDate, debounced.endDate],
    queryFn: ({ signal }) => leaveApi.preview({ leaveTypeCode: debounced.leaveTypeCode, startDate: debounced.startDate, endDate: debounced.endDate }, signal),
    enabled: canPreview,
  })

  const submit = useMutation({
    mutationFn: () => leaveApi.submit({ ...form, reason: form.reason || undefined, clientRequestId: clientRequestId.current },
      prefill.fromAssistant ? 'CHAT' : 'WEB'),
    onSuccess: (r) => {
      toast.success(t('apply.submitted', { id: r.id, name: r.managerApprover?.name ?? 'HR' }))
      qc.invalidateQueries({ queryKey: ['me'] })
      nav(`/requests/${r.id}`)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.fieldErrors.length) {
        setFieldErrors(Object.fromEntries(e.fieldErrors.map((f) => [f.field, f.message])))
      }
      toast.error(errorMessage(e))
    },
  })

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    const v = e.target.value
    setForm((f) => {
      const next = { ...f, [k]: v }
      if (k === 'startDate' && (!f.endDate || f.endDate < v)) next.endDate = v
      return next
    })
    setFieldErrors((fe) => ({ ...fe, [k]: '' }))
  }

  const localErrors: Record<string, string> = {}
  if (form.startDate && form.endDate && form.endDate < form.startDate) localErrors.endDate = t('errors.INVALID_DATE_RANGE')
  const blocked = !form.leaveTypeCode || !form.startDate || !form.endDate || !!localErrors.endDate
    || (preview.data ? !preview.data.valid : false)

  return (
    <div>
      <PageHeader title={t('apply.title')} subtitle={t('apply.subtitle')} />
      {prefill.fromAssistant && (
        <Alert tone="info" className="mb-4" icon={<Sparkles className="h-4 w-4" />}>{t('apply.fromAssistant')}</Alert>
      )}
      <div className="grid gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <form className="space-y-5 p-5" onSubmit={(e) => { e.preventDefault(); if (!blocked) submit.mutate() }} noValidate>
            <div>
              <Label htmlFor="type">{t('apply.leaveType')}</Label>
              <Select id="type" value={form.leaveTypeCode} onChange={set('leaveTypeCode')} required>
                <option value="">—</option>
                {types.data?.map((ty) => (
                  <option key={ty.code} value={ty.code}>{t(`leaveType.${ty.code}`, ty.displayName)} ({ty.annualEntitlement}/yr)</option>
                ))}
              </Select>
              <FieldError>{fieldErrors.leaveTypeCode}</FieldError>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <Label htmlFor="start">{t('apply.startDate')}</Label>
                <Input id="start" type="date" value={form.startDate} min={form.leaveTypeCode === 'SICK' ? undefined : isoToday()} onChange={set('startDate')} required />
                <FieldError>{fieldErrors.startDate}</FieldError>
              </div>
              <div>
                <Label htmlFor="end">{t('apply.endDate')}</Label>
                <Input id="end" type="date" value={form.endDate} min={form.startDate || undefined} onChange={set('endDate')} required aria-invalid={!!localErrors.endDate} />
                <FieldError>{localErrors.endDate || fieldErrors.endDate}</FieldError>
              </div>
            </div>
            <div>
              <Label htmlFor="reason">{t('apply.reason')}</Label>
              <Textarea id="reason" value={form.reason} onChange={set('reason')} maxLength={500} placeholder={t('apply.reasonPlaceholder')} />
            </div>
            <div className="flex justify-end">
              <Button type="submit" size="lg" loading={submit.isPending} disabled={blocked}>{submit.isPending ? t('apply.submitting') : t('apply.submit')}</Button>
            </div>
          </form>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader title={t('apply.preview')} />
          <div className="p-5">
            <PreviewPanel preview={canPreview ? preview.data : undefined} loading={canPreview && preview.isFetching} />
          </div>
        </Card>
      </div>
    </div>
  )
}
