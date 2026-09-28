import * as React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Toaster } from 'sonner'
import { AuthProvider } from '../auth/AuthProvider'
import { HomeRedirect, RequireAuth, RequireRole } from '../auth/guards'
import { AppShell } from '../components/layout/AppShell'
import { LoginPage } from '../features/auth/LoginPage'
import { EmployeeDashboard } from '../features/employee/EmployeeDashboard'
import { ApplyLeavePage } from '../features/employee/ApplyLeavePage'
import { MyRequestsPage } from '../features/employee/MyRequestsPage'
import { RequestDetailPage } from '../features/shared/RequestDetailPage'
import { NotFoundPage } from '../features/shared/NotFoundPage'
import { ManagerApprovalsPage, ManagerConflictsPage, ManagerDashboard, ManagerTeamPage } from '../features/manager/ManagerPages'
import { AuditPage, EscalationsPage, HrApprovalsPage, HrDashboard, OverviewPage } from '../features/hr/HrPages'
import { ErrorBoundary } from './ErrorBoundary'
import { ApiError } from '../api/client'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (count, err) => !(err instanceof ApiError && err.status >= 400 && err.status < 500) && count < 1,
      refetchOnWindowFocus: true,
      staleTime: 10_000,
    },
    mutations: { retry: false },
  },
})

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <ErrorBoundary>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route element={<RequireAuth />}>
                <Route element={<AppShell />}>
                  <Route index element={<HomeRedirect />} />
                  <Route path="/me" element={<EmployeeDashboard />} />
                  <Route path="/me/apply" element={<ApplyLeavePage />} />
                  <Route path="/me/requests" element={<MyRequestsPage />} />
                  <Route path="/requests/:id" element={<RequestDetailPage />} />
                  <Route element={<RequireRole roles={['MANAGER']} />}>
                    <Route path="/manager" element={<ManagerDashboard />} />
                    <Route path="/manager/approvals" element={<ManagerApprovalsPage />} />
                    <Route path="/manager/team" element={<ManagerTeamPage />} />
                    <Route path="/manager/conflicts" element={<ManagerConflictsPage />} />
                  </Route>
                  <Route element={<RequireRole roles={['HR']} />}>
                    <Route path="/hr" element={<HrDashboard />} />
                    <Route path="/hr/approvals" element={<HrApprovalsPage />} />
                    <Route path="/hr/escalations" element={<EscalationsPage />} />
                    <Route path="/hr/overview" element={<OverviewPage />} />
                    <Route path="/hr/audit" element={<AuditPage />} />
                  </Route>
                  <Route path="*" element={<NotFoundPage />} />
                </Route>
              </Route>
            </Routes>
          </ErrorBoundary>
        </AuthProvider>
      </BrowserRouter>
      <Toaster richColors position="top-right" closeButton />
    </QueryClientProvider>
  )
}

export default React.memo(App)
