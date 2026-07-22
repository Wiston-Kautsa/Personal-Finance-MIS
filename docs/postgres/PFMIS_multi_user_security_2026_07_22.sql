-- PFMIS server-side multi-user foundation for PostgreSQL.
-- Apply through a controlled migration tool. The application backend, not the client,
-- must set pfmis.user_id and pfmis.is_super_admin after authenticating the request.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$ BEGIN
    CREATE TYPE pfmis_user_role AS ENUM ('SUPER_ADMIN', 'USER');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE pfmis_user_status AS ENUM ('ACTIVE', 'INACTIVE');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS pfmis_users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(40) NOT NULL,
    full_name           VARCHAR(160) NOT NULL,
    email               VARCHAR(254),
    password_hash       TEXT NOT NULL,
    role                pfmis_user_role NOT NULL DEFAULT 'USER',
    status              pfmis_user_status NOT NULL DEFAULT 'ACTIVE',
    failed_login_count  INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pfmis_users_username_ci
    ON pfmis_users (lower(username));
CREATE UNIQUE INDEX IF NOT EXISTS uq_pfmis_users_email_ci
    ON pfmis_users (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS pfmis_authentication_log (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT REFERENCES pfmis_users(id),
    acting_user_id      BIGINT REFERENCES pfmis_users(id),
    event_type          VARCHAR(40) NOT NULL,
    success             BOOLEAN NOT NULL DEFAULT FALSE,
    details             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add an owner to every user-owned table. Repeat this pattern for all financial tables.
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE categories ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE project_activities ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE people ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE goals ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE goal_steps ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE household_budget_members ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE ai_settings ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE system_event_log ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);
ALTER TABLE ai_interaction_log ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES pfmis_users(id);

CREATE INDEX IF NOT EXISTS idx_accounts_owner ON accounts(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_categories_owner ON categories(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_projects_owner ON projects(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_project_activities_owner ON project_activities(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_people_owner ON people(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_goals_owner ON goals(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_goal_steps_owner ON goal_steps(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_budgets_owner ON budgets(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_household_members_owner ON household_budget_members(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_owner_date ON transactions(owner_user_id, transaction_date);
CREATE INDEX IF NOT EXISTS idx_ai_settings_owner ON ai_settings(owner_user_id);

CREATE OR REPLACE FUNCTION pfmis_session_user_id()
RETURNS BIGINT
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('pfmis.user_id', true), '')::BIGINT
$$;

CREATE OR REPLACE FUNCTION pfmis_session_is_super_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(NULLIF(current_setting('pfmis.is_super_admin', true), '')::BOOLEAN, FALSE)
$$;

-- Apply RLS to every user-owned table.
DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'accounts', 'categories', 'projects', 'project_activities', 'people',
        'goals', 'goal_steps', 'budgets', 'household_budget_members',
        'transactions', 'ai_settings', 'system_event_log', 'ai_interaction_log'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format('DROP POLICY IF EXISTS pfmis_owner_select ON %I', table_name);
        EXECUTE format('DROP POLICY IF EXISTS pfmis_owner_insert ON %I', table_name);
        EXECUTE format('DROP POLICY IF EXISTS pfmis_owner_update ON %I', table_name);
        EXECUTE format('DROP POLICY IF EXISTS pfmis_owner_delete ON %I', table_name);

        EXECUTE format(
            'CREATE POLICY pfmis_owner_select ON %I FOR SELECT USING '
            || '(pfmis_session_is_super_admin() OR owner_user_id = pfmis_session_user_id())',
            table_name
        );
        EXECUTE format(
            'CREATE POLICY pfmis_owner_insert ON %I FOR INSERT WITH CHECK '
            || '(owner_user_id = pfmis_session_user_id() '
            || 'OR (pfmis_session_is_super_admin() AND owner_user_id IS NOT NULL))',
            table_name
        );
        EXECUTE format(
            'CREATE POLICY pfmis_owner_update ON %I FOR UPDATE USING '
            || '(pfmis_session_is_super_admin() OR owner_user_id = pfmis_session_user_id()) '
            || 'WITH CHECK (pfmis_session_is_super_admin() OR owner_user_id = pfmis_session_user_id())',
            table_name
        );
        EXECUTE format(
            'CREATE POLICY pfmis_owner_delete ON %I FOR DELETE USING '
            || '(pfmis_session_is_super_admin() OR owner_user_id = pfmis_session_user_id())',
            table_name
        );
    END LOOP;
END $$;

-- Assign the authenticated owner automatically. The backend may provide another
-- owner only when the authenticated session is a Super Administrator.
CREATE OR REPLACE FUNCTION pfmis_assign_owner()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    session_user_id BIGINT := pfmis_session_user_id();
BEGIN
    IF session_user_id IS NULL THEN
        RAISE EXCEPTION 'No authenticated PFMIS user is set for this database transaction';
    END IF;
    IF NEW.owner_user_id IS NULL THEN
        NEW.owner_user_id := session_user_id;
    ELSIF NEW.owner_user_id <> session_user_id AND NOT pfmis_session_is_super_admin() THEN
        RAISE EXCEPTION 'A normal user cannot create a record for another user';
    END IF;
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'accounts', 'categories', 'projects', 'project_activities', 'people',
        'goals', 'goal_steps', 'budgets', 'household_budget_members',
        'transactions', 'ai_settings', 'system_event_log', 'ai_interaction_log'
    ]
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_assign_pfmis_owner ON %I', table_name);
        EXECUTE format(
            'CREATE TRIGGER trg_assign_pfmis_owner BEFORE INSERT ON %I '
            || 'FOR EACH ROW EXECUTE FUNCTION pfmis_assign_owner()',
            table_name
        );
    END LOOP;
END $$;

-- The authenticated API service, not a desktop/client database role, should own
-- the user directory and authentication history. Do not grant direct client access.
REVOKE ALL ON TABLE pfmis_users FROM PUBLIC;
REVOKE ALL ON TABLE pfmis_authentication_log FROM PUBLIC;

-- Prevent ordinary users from changing record ownership after insert.
CREATE OR REPLACE FUNCTION pfmis_prevent_owner_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.owner_user_id IS DISTINCT FROM OLD.owner_user_id
       AND NOT pfmis_session_is_super_admin() THEN
        RAISE EXCEPTION 'Record ownership cannot be changed';
    END IF;
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'accounts', 'categories', 'projects', 'project_activities', 'people',
        'goals', 'goal_steps', 'budgets', 'household_budget_members',
        'transactions', 'ai_settings'
    ]
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_prevent_owner_change ON %I', table_name);
        EXECUTE format(
            'CREATE TRIGGER trg_prevent_owner_change BEFORE UPDATE ON %I '
            || 'FOR EACH ROW EXECUTE FUNCTION pfmis_prevent_owner_change()',
            table_name
        );
    END LOOP;
END $$;

COMMIT;
