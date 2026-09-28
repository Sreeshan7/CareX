import * as React from 'react'
import * as RD from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { cn } from '../../lib/utils'

/** Accessible modal (Radix: focus trap, Esc, aria). */
export function Modal({ open, onOpenChange, title, description, children, className }: {
  open: boolean
  onOpenChange: (o: boolean) => void
  title: React.ReactNode
  description?: React.ReactNode
  children: React.ReactNode
  className?: string
}) {
  return (
    <RD.Root open={open} onOpenChange={onOpenChange}>
      <RD.Portal>
        <RD.Overlay className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-[1px]" />
        <RD.Content className={cn('fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-white p-6 shadow-xl focus:outline-none', className)}>
          <div className="mb-4 flex items-start justify-between gap-4">
            <div>
              <RD.Title className="text-lg font-semibold text-slate-900">{title}</RD.Title>
              {description ? <RD.Description className="mt-1 text-sm text-slate-500">{description}</RD.Description>
                : <RD.Description className="sr-only">{typeof title === 'string' ? title : 'Dialog'}</RD.Description>}
            </div>
            <RD.Close className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600" aria-label="Close">
              <X className="h-5 w-5" />
            </RD.Close>
          </div>
          {children}
        </RD.Content>
      </RD.Portal>
    </RD.Root>
  )
}

/** Right-hand slide-over (full screen on mobile) used by the assistant. */
export function Sheet({ open, onOpenChange, title, children }: { open: boolean; onOpenChange: (o: boolean) => void; title: React.ReactNode; children: React.ReactNode }) {
  return (
    <RD.Root open={open} onOpenChange={onOpenChange} modal={false}>
      <RD.Portal>
        <RD.Content
          onInteractOutside={(e) => e.preventDefault()}
          className="fixed inset-0 z-50 flex flex-col bg-white shadow-2xl focus:outline-none sm:inset-y-0 sm:left-auto sm:right-0 sm:w-[440px] sm:border-l sm:border-slate-200"
        >
          <RD.Description className="sr-only">Assistant panel</RD.Description>
          {typeof title === 'string' ? <RD.Title className="sr-only">{title}</RD.Title> : <RD.Title asChild><div>{title}</div></RD.Title>}
          {children}
        </RD.Content>
      </RD.Portal>
    </RD.Root>
  )
}
