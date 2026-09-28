-- CareX Leave — core schema (implementation.md §6)
-- Constraint names are stable: GlobalExceptionHandler maps them to API error codes.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ============ ORG ============
CREATE TABLE team (
  id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name                   varchar(100) NOT NULL UNIQUE,
  manager_id             bigint NULL,
  conflict_threshold_pct numeric(5,2) NOT NULL DEFAULT 30.00
                         CHECK (conflict_threshold_pct > 0 AND conflict_threshold_pct <= 100),
  conflict_min_absent    int NOT NULL DEFAULT 2 CHECK (conflict_min_absent >= 1),
  created_at             timestamptz NOT NULL DEFAULT now(),
  updated_at             timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
  id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email              varchar(254) NOT NULL,
  full_name          varchar(120) NOT NULL,
  password_hash      varchar(100) NOT NULL,
  role               varchar(20)  NOT NULL CHECK (role IN ('EMPLOYEE','MANAGER','HR')),
  team_id            bigint NOT NULL REFERENCES team(id),
  joining_date       date NOT NULL,
  active             boolean NOT NULL DEFAULT true,
  preferred_language varchar(10) NOT NULL DEFAULT 'en-IN',
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  version            bigint NOT NULL DEFAULT 0,
  CONSTRAINT uq_user_email UNIQUE (email)
);
ALTER TABLE team ADD CONSTRAINT fk_team_manager FOREIGN KEY (manager_id) REFERENCES app_user(id);
CREATE INDEX ix_user_team ON app_user(team_id) WHERE active;

-- ============ REFERENCE ============
CREATE TABLE leave_type (
  code                  varchar(20) PRIMARY KEY,
  display_name          varchar(60) NOT NULL,
  annual_entitlement    numeric(5,1) NOT NULL CHECK (annual_entitlement >= 0),
  prorated              boolean NOT NULL DEFAULT true,
  backdate_days_allowed int NOT NULL DEFAULT 0,
  active                boolean NOT NULL DEFAULT true
);

CREATE TABLE holiday (
  holiday_date date PRIMARY KEY,
  name         varchar(100) NOT NULL
);

-- ============ BALANCE ============
CREATE TABLE leave_balance (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id          bigint NOT NULL REFERENCES app_user(id),
  leave_type_code  varchar(20) NOT NULL REFERENCES leave_type(code),
  year             int NOT NULL,
  entitled_days    numeric(5,1) NOT NULL CHECK (entitled_days >= 0),
  adjustment_days  numeric(5,1) NOT NULL DEFAULT 0,
  used_days        numeric(5,1) NOT NULL DEFAULT 0 CHECK (used_days >= 0),
  pending_days     numeric(5,1) NOT NULL DEFAULT 0 CHECK (pending_days >= 0),
  proration_basis  varchar(200) NOT NULL,
  version          bigint NOT NULL DEFAULT 0,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_balance_user_type_year UNIQUE (user_id, leave_type_code, year),
  CONSTRAINT ck_balance_not_overdrawn CHECK (used_days + pending_days <= entitled_days + adjustment_days)
);

-- ============ LEAVE REQUEST ============
CREATE TABLE leave_request (
  id                     bigint GENERATED ALWAYS AS IDENTITY (START WITH 1001) PRIMARY KEY,
  employee_id            bigint NOT NULL REFERENCES app_user(id),
  leave_type_code        varchar(20) NOT NULL REFERENCES leave_type(code),
  start_date             date NOT NULL,
  end_date               date NOT NULL,
  working_days           numeric(5,1) NOT NULL CHECK (working_days > 0),
  reason                 varchar(500),
  status                 varchar(24) NOT NULL CHECK (status IN
                           ('PENDING_MANAGER','MANAGER_ESCALATED','PENDING_HR','HR_ESCALATED',
                            'APPROVED','REJECTED','CANCELLED')),
  team_id                bigint NOT NULL REFERENCES team(id),
  manager_approver_id    bigint NOT NULL REFERENCES app_user(id),
  manager_routed_to_hr   boolean NOT NULL DEFAULT false,  -- top-level user: HR head acts as manager-stage proxy
  escalation_approver_id bigint NULL REFERENCES app_user(id),
  escalated_to_hr_pool   boolean NOT NULL DEFAULT false,
  manager_decided_by     bigint NULL REFERENCES app_user(id),
  hr_decided_by          bigint NULL REFERENCES app_user(id),
  stage_entered_at       timestamptz NOT NULL,
  stage_deadline_at      timestamptz NULL,
  client_request_id      uuid NOT NULL,
  channel                varchar(10) NOT NULL DEFAULT 'WEB' CHECK (channel IN ('WEB','CHAT','VOICE')),
  decided_at             timestamptz NULL,
  cancelled_at           timestamptz NULL,
  created_at             timestamptz NOT NULL DEFAULT now(),
  updated_at             timestamptz NOT NULL DEFAULT now(),
  version                bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_request_dates     CHECK (end_date >= start_date),
  CONSTRAINT ck_request_same_year CHECK (date_part('year', start_date) = date_part('year', end_date)),
  CONSTRAINT uq_request_client_id UNIQUE (employee_id, client_request_id),
  CONSTRAINT ex_request_no_overlap EXCLUDE USING gist (
      employee_id WITH =,
      daterange(start_date, end_date, '[]') WITH &&
  ) WHERE (status IN ('PENDING_MANAGER','MANAGER_ESCALATED','PENDING_HR','HR_ESCALATED','APPROVED'))
);
CREATE INDEX ix_request_employee      ON leave_request(employee_id, start_date DESC);
CREATE INDEX ix_request_manager_queue ON leave_request(manager_approver_id, status);
CREATE INDEX ix_request_escalation_q  ON leave_request(escalation_approver_id, status)
       WHERE escalation_approver_id IS NOT NULL;
CREATE INDEX ix_request_status        ON leave_request(status);
CREATE INDEX ix_request_deadline      ON leave_request(stage_deadline_at)
       WHERE status IN ('PENDING_MANAGER','PENDING_HR');
CREATE INDEX ix_request_team_range    ON leave_request USING gist (team_id, daterange(start_date, end_date, '[]'))
       WHERE status IN ('PENDING_MANAGER','MANAGER_ESCALATED','PENDING_HR','HR_ESCALATED','APPROVED');

-- ============ APPROVAL ACTIONS (timeline) ============
CREATE TABLE approval_action (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id      bigint NOT NULL REFERENCES leave_request(id),
  stage           varchar(10) NOT NULL CHECK (stage IN ('NONE','MANAGER','HR')),
  action          varchar(12) NOT NULL CHECK (action IN ('SUBMIT','APPROVE','REJECT','ESCALATE','CANCEL')),
  actor_id        bigint NULL REFERENCES app_user(id),
  actor_capacity  varchar(24) NOT NULL CHECK (actor_capacity IN
                    ('OWNER','ASSIGNED_MANAGER','ESCALATION_MANAGER','HR_PROXY_MANAGER','HR','SYSTEM')),
  from_status     varchar(24) NULL,
  to_status       varchar(24) NOT NULL,
  comment         varchar(1000),
  conflict_acknowledged boolean NOT NULL DEFAULT false,
  channel         varchar(10) NOT NULL DEFAULT 'WEB',
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_action_request ON approval_action(request_id, created_at);
CREATE UNIQUE INDEX uq_action_one_decision_per_stage
  ON approval_action(request_id, stage) WHERE action IN ('APPROVE','REJECT');

-- ============ CONFLICT FLAG ============
CREATE TABLE conflict_flag (
  id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id              bigint NOT NULL UNIQUE REFERENCES leave_request(id),
  flagged                 boolean NOT NULL,
  peak_date               date NULL,
  peak_absent_count       int NOT NULL,
  team_size               int NOT NULL,
  threshold_pct           numeric(5,2) NOT NULL,
  min_absent              int NOT NULL,
  overlapping_request_ids jsonb NOT NULL DEFAULT '[]',
  day_breakdown           jsonb NOT NULL,
  evaluated_at            timestamptz NOT NULL,
  evaluated_on            varchar(20) NOT NULL,
  acknowledged_by_manager bigint NULL REFERENCES app_user(id),
  acknowledged_by_hr      bigint NULL REFERENCES app_user(id),
  version                 bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_conflict_flagged ON conflict_flag(flagged) WHERE flagged;

-- ============ ESCALATION ============
CREATE TABLE escalation (
  id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id           bigint NOT NULL REFERENCES leave_request(id),
  stage                varchar(10) NOT NULL CHECK (stage IN ('MANAGER','HR')),
  deadline_at          timestamptz NOT NULL,
  escalated_at         timestamptz NOT NULL,
  escalated_to_user_id bigint NULL REFERENCES app_user(id),
  escalated_to_role    varchar(20) NULL,
  resolved_at          timestamptz NULL,
  resolution           varchar(20) NULL CHECK (resolution IN ('APPROVED','REJECTED','CANCELLED')),
  CONSTRAINT uq_escalation_request_stage UNIQUE (request_id, stage)
);
CREATE INDEX ix_escalation_open ON escalation(escalated_at) WHERE resolved_at IS NULL;

-- ============ NOTIFICATION ============
CREATE TABLE notification (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  recipient_id bigint NOT NULL REFERENCES app_user(id),
  type         varchar(40) NOT NULL,
  request_id   bigint NULL REFERENCES leave_request(id),
  title        varchar(200) NOT NULL,
  body         varchar(1000) NOT NULL,
  read_at      timestamptz NULL,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_inbox ON notification(recipient_id, created_at DESC);

-- ============ AUDIT (append-only) ============
CREATE TABLE audit_log (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  occurred_at    timestamptz NOT NULL DEFAULT now(),
  actor_id       bigint NULL REFERENCES app_user(id),
  actor_role     varchar(20) NOT NULL,
  channel        varchar(10) NOT NULL,
  action         varchar(50) NOT NULL,
  entity_type    varchar(30) NOT NULL,
  entity_id      bigint NULL,
  summary        varchar(500) NOT NULL,
  before_state   jsonb NULL,
  after_state    jsonb NULL,
  correlation_id varchar(64) NULL,
  ip_address     varchar(45) NULL
);
CREATE INDEX ix_audit_entity ON audit_log(entity_type, entity_id, occurred_at);
CREATE INDEX ix_audit_time   ON audit_log(occurred_at DESC);
CREATE INDEX ix_audit_actor  ON audit_log(actor_id, occurred_at DESC);

CREATE FUNCTION audit_log_immutable() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'audit_log is append-only'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_audit_no_update BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

-- ============ ASSISTANT TELEMETRY (no transcripts stored) ============
CREATE TABLE assistant_interaction (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     bigint NOT NULL REFERENCES app_user(id),
  channel     varchar(10) NOT NULL,
  language    varchar(10) NOT NULL,
  intent      varchar(40) NOT NULL,
  outcome     varchar(30) NOT NULL,
  provider    varchar(20) NOT NULL,
  latency_ms  int NOT NULL,
  error_code  varchar(40) NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);
