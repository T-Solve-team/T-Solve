import { Client } from 'pg';

export async function seedDevData(): Promise<void> {
  const required = (name: string): string => {
    const value = process.env[name];
    if (!value) throw new Error(`missing dev database setting: ${name}`);
    return value;
  };
  const client = new Client({
    host: required('DB_HOST'),
    port: Number(required('DB_PORT')),
    database: required('DB_NAME'),
    user: required('DB_USER'),
    password: required('DB_PASSWORD'),
    connectionTimeoutMillis: 3_000,
  });
  await client.connect();
  try {
    await client.query('BEGIN');
    // The dev profile uses the single DB_* database; never create accounts or change existing rows.
    const alice = await client.query<{ id: string }>(
      "SELECT id FROM app_user WHERE email = $1 AND status = 'ACTIVE'",
      ['alice@acme.test'],
    );
    if (alice.rowCount !== 1) throw new Error('dev profile must seed active alice@acme.test first');
    await client.query(
      `INSERT INTO user_role (user_id, role, assigned_at)
       SELECT $1, role, now() FROM unnest($2::text[]) AS role
       ON CONFLICT (user_id, role) DO NOTHING`,
      [alice.rows[0].id, ['TICKET_RESOLVER', 'PROJECT_MANAGER']],
    );
    await client.query(
      `INSERT INTO department (name) VALUES ($1) ON CONFLICT (name) DO NOTHING`,
      ['E2E ACME'],
    );
    await client.query(
      `INSERT INTO project (department_id, jira_project_key)
       SELECT department_id, $1 FROM department WHERE name = $2
       ON CONFLICT (jira_project_key) DO NOTHING`,
      ['E2EACME', 'E2E ACME'],
    );
    await client.query(
      `INSERT INTO ticket (source_ticket_id, project_id, source, status, title,
                           symptoms, root_cause, resolution_steps, scope, writer_id)
       SELECT $1, public_id, 'MANUAL', 'PUBLISHED', 'ACME E2E smoke ticket',
              'Smoke fixture symptom', 'Smoke fixture cause', 'Smoke fixture resolution',
              'ACME local E2E', $2
       FROM project WHERE jira_project_key = $3
         AND NOT EXISTS (SELECT 1 FROM ticket WHERE status = 'PUBLISHED')
       ON CONFLICT (source_ticket_id) DO NOTHING`,
      ['E2E-ACME-1', alice.rows[0].id, 'E2EACME'],
    );
    await client.query('COMMIT');
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    await client.end();
  }
}
