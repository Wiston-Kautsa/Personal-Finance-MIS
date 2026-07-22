-- PFMIS PostgreSQL extension migration
-- Date: 2026-07-22
-- Purpose:
--   Add monthly budgets, monthly household roster records, valid transaction reporting,
--   AI interaction audit metadata, and supporting indexes/constraints for production PostgreSQL.
--
-- Notes:
--   The current desktop prototype in this workspace still initializes through the existing
--   Java DatabaseHandler. Apply this script in the production PostgreSQL migration layer,
--   adjusting table/column names only if your deployed schema already differs.

begin;

create table if not exists schema_version (
    version integer primary key,
    description text not null,
    applied_at timestamptz not null default now()
);

insert into schema_version (version, description)
values (2026072201, 'Budget household and AI audit extension')
on conflict (version) do nothing;

create table if not exists budgets (
    id bigserial primary key,
    budget_name text not null,
    category_id bigint references categories(id),
    budget_month char(7) not null,
    amount_limit numeric(18, 2) not null,
    rollover boolean not null default false,
    status text not null default 'PLANNED',
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    constraint chk_budgets_name_not_blank check (btrim(budget_name) <> ''),
    constraint chk_budgets_month_format check (budget_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    constraint chk_budgets_amount_limit_positive check (amount_limit > 0),
    constraint chk_budgets_status check (status in ('PLANNED', 'ON_BUDGET', 'FULFILLED', 'NOT_MET', 'PAUSED', 'CLOSED'))
);

create table if not exists household_budget_members (
    id bigserial primary key,
    budget_month char(7) not null,
    person_name text not null,
    relationship text,
    presence_status text not null default 'IN_HOUSE',
    joined_date date,
    left_date date,
    share_weight numeric(8, 3) not null default 1,
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    constraint chk_household_budget_month_format check (budget_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    constraint chk_household_person_not_blank check (btrim(person_name) <> ''),
    constraint chk_household_presence_status check (presence_status in ('IN_HOUSE', 'JOINED', 'LEFT', 'AWAY')),
    constraint chk_household_share_weight_non_negative check (share_weight >= 0),
    constraint chk_household_dates_order check (left_date is null or joined_date is null or left_date >= joined_date)
);

create table if not exists ai_interaction_log (
    id bigserial primary key,
    module_name text not null,
    action_name text not null,
    provider_name text not null,
    status text not null,
    created_at timestamptz not null default now(),
    constraint chk_ai_interaction_module_not_blank check (btrim(module_name) <> ''),
    constraint chk_ai_interaction_action_not_blank check (btrim(action_name) <> ''),
    constraint chk_ai_interaction_status check (status in ('SUCCESS', 'FAILED', 'UNKNOWN'))
);

create or replace view valid_transactions as
select *
from transactions
where coalesce(transaction_status, 'COMPLETED') <> 'CANCELLED';

create index if not exists idx_transactions_date
on transactions(transaction_date);

create index if not exists idx_transactions_account
on transactions(account_id);

create index if not exists idx_transactions_project
on transactions(project_id);

create index if not exists idx_transactions_person
on transactions(person_id);

create index if not exists idx_transactions_type_status
on transactions(transaction_type, transaction_status);

create index if not exists idx_transactions_category_month
on transactions(category_id, transaction_date);

create index if not exists idx_project_activities_project
on project_activities(project_id);

create index if not exists idx_budgets_month
on budgets(budget_month);

create index if not exists idx_budgets_category_month
on budgets(category_id, budget_month);

create index if not exists idx_household_budget_members_month
on household_budget_members(budget_month);

create index if not exists idx_household_budget_members_month_name
on household_budget_members(budget_month, lower(person_name));

create index if not exists idx_ai_interaction_log_created
on ai_interaction_log(created_at desc);

commit;
