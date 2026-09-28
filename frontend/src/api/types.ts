// Mirrors backend DTOs (backend/.../leave/query/Views.java, AuthController, controllers).

export type Role = 'EMPLOYEE' | 'MANAGER' | 'HR'
export type LeaveStatus =
  | 'PENDING_MANAGER' | 'MANAGER_ESCALATED' | 'PENDING_HR' | 'HR_ESCALATED'
  | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type Stage = 'MANAGER' | 'HR' | 'NONE'
export type AllowedAction = 'MANAGER_APPROVE' | 'MANAGER_REJECT' | 'HR_APPROVE' | 'HR_REJECT' | 'CANCEL'
export type Channel = 'WEB' | 'CHAT' | 'VOICE' | 'SYSTEM'

export interface UserView {
  id: number
  name: string
  email: string
  role: Role
  teamId: number
  teamName: string
  preferredLanguage: string
  joiningDate: string
  managedTeamIds: number[]
}

export interface LoginResponse { accessToken: string; expiresAt: string; user: UserView }
export interface DemoAccount { key: string; name: string; role: Role; teamName: string; description: string }

export interface PersonRef { id: number; name: string }
export interface EmployeeRef { id: number; name: string; teamName: string }

export interface TimelineEntry {
  id: number; stage: Stage; action: 'SUBMIT' | 'APPROVE' | 'REJECT' | 'ESCALATE' | 'CANCEL'
  actor: PersonRef | null; actorCapacity: string; fromStatus: LeaveStatus | null; toStatus: LeaveStatus
  comment: string | null; conflictAcknowledged: boolean; channel: Channel; at: string
}

export interface EscalationView {
  id: number; requestId: number; stage: 'MANAGER' | 'HR'; deadlineAt: string; escalatedAt: string
  target: PersonRef | null; targetRole: string | null; resolvedAt: string | null; resolution: string | null
  lateBySeconds: number; employee: EmployeeRef; requestStatus: LeaveStatus; startDate: string; endDate: string
}

export interface DayView { date: string; absent: number; teamSize: number; ratio: number; over: boolean; people: string[] | null }

export interface ConflictView {
  flagged: boolean; peakDate: string | null; peakAbsent: number; teamSize: number; thresholdPct: number
  minAbsent: number; days: DayView[]; snapshotFlagged: boolean | null; snapshotEvaluatedOn: string | null
  acknowledgedByManager: string | null; acknowledgedByHr: string | null; namesVisible: boolean
}

export interface LeaveRequestDetail {
  id: number; employee: EmployeeRef; leaveTypeCode: string; leaveTypeName: string
  startDate: string; endDate: string; workingDays: number; reason: string | null
  status: LeaveStatus; stage: Stage; stageEnteredAt: string; stageDeadlineAt: string | null; channel: Channel
  managerApprover: PersonRef | null; managerRoutedToHr: boolean; escalationApprover: PersonRef | null
  escalatedToHrPool: boolean; managerDecidedBy: PersonRef | null; hrDecidedBy: PersonRef | null
  escalations: EscalationView[]; conflict: ConflictView | null; timeline: TimelineEntry[]
  allowedActions: AllowedAction[]; createdAt: string; decidedAt: string | null; cancelledAt: string | null; version: number
}

export interface LeaveRequestSummary {
  id: number; employee: EmployeeRef; leaveTypeCode: string; startDate: string; endDate: string
  workingDays: number; status: LeaveStatus; stage: Stage; stageEnteredAt: string; stageDeadlineAt: string | null
  escalated: boolean; flagged: boolean; channel: Channel; createdAt: string; allowedActions: AllowedAction[]
}

export interface PageView<T> { items: T[]; page: number; size: number; total: number }

export interface BalanceView {
  leaveTypeCode: string; leaveTypeName: string; year: number; entitled: number; adjustment: number
  used: number; pending: number; available: number; prorationBasis: string
}

export interface LeaveTypeView { code: string; displayName: string; annualEntitlement: number; prorated: boolean; backdateDaysAllowed: number }
export interface HolidayView { date: string; name: string }

export interface PreviewResponse {
  valid: boolean; workingDays: number; workingDates: string[]
  excludedDates: { date: string; reason: 'WEEKEND' | 'HOLIDAY'; holidayName: string | null }[]
  availableBefore: number | null; availableAfter: number | null
  conflict: { wouldFlag: boolean; peakDate: string | null; peakAbsent: number; teamSize: number; thresholdPct: number } | null
  errors: { code: string; message: string }[]
}

export interface NotificationView { id: number; type: string; requestId: number | null; title: string; body: string; readAt: string | null; createdAt: string }

export interface TeamCalendar {
  from: string; to: string
  members: { id: number; name: string; teamId: number }[]
  entries: { requestId: number; employeeId: number; startDate: string; endDate: string; status: LeaveStatus; leaveTypeCode: string; flagged: boolean }[]
  daysByTeam: Record<string, { date: string; workingDay: boolean; absent: number; teamSize: number; over: boolean }[]>
  teams: { id: number; name: string; thresholdPct: number; minAbsent: number; size: number }[]
}

export interface ManagerDashboard {
  pending: number; escalated: number; flagged: number; outToday: number; outThisWeek: number; teamSize: number
  teams: { id: number; name: string }[]; queuePreview: LeaveRequestSummary[]
}

export interface HrOverview {
  year: number; totalRequests: number; byStatus: Record<string, number>; byType: Record<string, number>
  activeDaysByTeam: Record<string, number>; flaggedActive: number; openEscalations: number; pendingHr: number
  avgApprovalHours: number; upcomingAbsences: LeaveRequestSummary[]; headcount: number
}

export interface AuditView {
  id: number; occurredAt: string; actorId: number | null; actorName: string; actorRole: string; channel: string
  action: string; entityType: string; entityId: number | null; summary: string
  beforeState: Record<string, unknown> | null; afterState: Record<string, unknown> | null; correlationId: string | null
}

// ---------------- Assistant ----------------
export type AssistantIntent =
  | 'APPLY_LEAVE' | 'CANCEL_LEAVE' | 'APPROVE_REQUEST' | 'REJECT_REQUEST' | 'QUERY_BALANCE' | 'QUERY_MY_REQUESTS'
  | 'QUERY_REQUEST_STATUS' | 'QUERY_PENDING_APPROVALS' | 'QUERY_TEAM_LEAVE' | 'QUERY_CONFLICTS' | 'QUERY_ESCALATIONS'
  | 'QUERY_LEAVE_OVERVIEW' | 'QUERY_HOLIDAYS' | 'POLICY_HELP' | 'GREETING' | 'UNKNOWN'

export interface Slot<T = string | number> { value: T; source: 'EXPLICIT' | 'RESOLVED' | 'INFERRED'; expression?: string | null }

export interface CanonicalCommand {
  schemaVersion: string
  intent: AssistantIntent
  language: string
  slots: Record<string, Slot | null>
  missingSlots: string[]
  ambiguities: { slot: string; reason: string }[]
  status: 'READY_FOR_CONFIRMATION' | 'NEEDS_CLARIFICATION' | 'POLICY_DENIED' | 'ANSWERED' | 'UNSUPPORTED'
}

export interface ProposedAction {
  type: 'SUBMIT_LEAVE' | 'CANCEL_LEAVE' | 'DECIDE_REQUEST'
  payload: Record<string, any>
  preview: PreviewResponse | null
  request: LeaveRequestSummary | null
  conflict: ConflictView | null
  requiresConfirmation: boolean
}

export interface AssistantReply {
  replyText: string
  replyTextEnglish: string
  replyLanguage: string
  command: CanonicalCommand
  clarification: { slot: string; question: string; options: { label: string; value: string }[] } | null
  proposedAction: ProposedAction | null
  cards: { kind: string; data: any }[]
  degraded: boolean
  degradedReason: string | null
}

export interface AssistantStatus {
  provider: string; sttAvailable: boolean; llmAvailable: boolean; ttsAvailable: boolean; translateAvailable: boolean
  languages: { code: string; name: string }[]
}
