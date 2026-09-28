import * as React from 'react'

export type RecorderState = 'idle' | 'recording' | 'unsupported' | 'denied'

const MAX_MS = 29_000 // Sarvam REST STT accepts < 30 s

/** MediaRecorder wrapper: tap to start, tap to stop, auto-stop before 30 s. Audio is never stored. */
export function useVoiceRecorder(onAudio: (blob: Blob) => void) {
  const [state, setState] = React.useState<RecorderState>(
    typeof window !== 'undefined' && 'MediaRecorder' in window && !!navigator.mediaDevices?.getUserMedia ? 'idle' : 'unsupported')
  const [elapsed, setElapsed] = React.useState(0)
  const rec = React.useRef<MediaRecorder | null>(null)
  const timer = React.useRef<number | null>(null)
  const chunks = React.useRef<Blob[]>([])

  const stop = React.useCallback(() => {
    if (rec.current && rec.current.state !== 'inactive') rec.current.stop()
  }, [])

  const start = React.useCallback(async () => {
    if (state === 'unsupported') return
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const mime = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4']
        .find((m) => MediaRecorder.isTypeSupported(m))
      const r = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined)
      chunks.current = []
      r.ondataavailable = (e) => { if (e.data.size > 0) chunks.current.push(e.data) }
      r.onstop = () => {
        stream.getTracks().forEach((t) => t.stop())
        if (timer.current) window.clearInterval(timer.current)
        setState('idle')
        setElapsed(0)
        const type = (r.mimeType || 'audio/webm').split(';')[0]
        const blob = new Blob(chunks.current, { type })
        if (blob.size > 0) onAudio(blob)
      }
      rec.current = r
      r.start()
      setState('recording')
      const t0 = Date.now()
      timer.current = window.setInterval(() => {
        const ms = Date.now() - t0
        setElapsed(Math.floor(ms / 1000))
        if (ms >= MAX_MS) stop()
      }, 250)
    } catch {
      setState('denied')
    }
  }, [onAudio, state, stop])

  React.useEffect(() => () => { if (timer.current) window.clearInterval(timer.current) }, [])

  return { state, elapsed, start, stop }
}
