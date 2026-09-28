import * as React from 'react'

export class ErrorBoundary extends React.Component<{ children: React.ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  render() {
    if (this.state.error) {
      return (
        <div className="mx-auto mt-24 max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm" role="alert">
          <p className="text-lg font-semibold text-slate-900">Something went wrong</p>
          <p className="mt-2 text-sm text-slate-600">The page hit an unexpected error. Your data is safe on the server.</p>
          <button className="mt-6 rounded-lg bg-brand-700 px-4 py-2 text-sm font-medium text-white" onClick={() => window.location.assign('/')}>
            Reload
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
