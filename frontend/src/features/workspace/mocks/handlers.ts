import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { WorkspaceInfo } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevUser } from '@/shared/auth';
import type { MsgCode, MsgParams } from '@/shared/messages';

let workspace: WorkspaceInfo | undefined;

const FIELDS = [
  ['companyName', 'Company name', 100],
  ['jiraSiteUrl', 'Jira site address', 200],
  ['serviceAccountEmail', 'Service account email', 100],
] as const;

function accessError() {
  const user = getDevUser();
  if (!user) return problem(401, 'MSG07');
  if (user !== 'admin' && user !== 'admin-tr') return problem(403, 'MSG08');
  return undefined;
}

function fieldError(field: string, code: MsgCode, params?: MsgParams) {
  return problem(400, code, { params, fieldErrors: [{ field, code, params }] });
}

function isRecord(input: unknown): input is Record<string, unknown> {
  return typeof input === 'object' && input !== null && !Array.isArray(input);
}

function validate(input: Record<string, unknown>, isSetup: boolean) {
  for (const [field, label, max] of FIELDS) {
    if (!isSetup && field === 'jiraSiteUrl') continue;
    const value = input[field];
    if (typeof value !== 'string' || !value.trim())
      return fieldError(field, 'MSG01', { field_name: label });
    if (value.length > max)
      return fieldError(field, 'MSG02', { field_name: label, max_length: max });
  }
  if (
    isSetup &&
    (typeof input.serviceAccountToken !== 'string' || !input.serviceAccountToken.trim())
  )
    return fieldError('serviceAccountToken', 'MSG01', { field_name: 'Service account API token' });
  if (
    typeof input.serviceAccountEmail === 'string' &&
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.serviceAccountEmail.trim())
  )
    return fieldError('serviceAccountEmail', 'MSG03');
  if (
    isSetup &&
    (typeof input.jiraSiteUrl !== 'string' ||
      !/^https:\/\/[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.atlassian\.net\/?$/i.test(
        input.jiraSiteUrl.trim(),
      ))
  )
    return fieldError('jiraSiteUrl', 'MSG61');
  return undefined;
}

/** Already registered by src/mocks/handlers.ts, ahead of the generated handlers. */
export const handlers: HttpHandler[] = [
  http.post('*/api/v1/workspace/setup', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accessError();
    if (denied) return denied;
    const input: unknown = await request.json().catch(() => null);
    if (!isRecord(input)) return problem(400);
    const invalid = validate(input, true);
    if (invalid) return invalid;
    if (workspace)
      return HttpResponse.json({ status: 409, code: 'WORKSPACE_ALREADY_SET_UP' }, { status: 409 });
    // Narrow the JSON boundary before constructing the generated response type. Never retain the token.
    if (
      typeof input.companyName !== 'string' ||
      typeof input.jiraSiteUrl !== 'string' ||
      typeof input.serviceAccountEmail !== 'string'
    )
      return problem(400);
    workspace = {
      companyName: input.companyName.trim(),
      jiraSiteUrl: input.jiraSiteUrl.trim().toLowerCase().replace(/\/$/, ''),
      serviceAccountEmail: input.serviceAccountEmail.trim(),
    };
    return HttpResponse.json<WorkspaceInfo>(workspace, { status: 201 });
  }),
  http.get('*/api/v1/workspace', async () => {
    await delay(MOCK_DELAY_MS);
    return (
      accessError() ??
      (workspace ? HttpResponse.json<WorkspaceInfo>(workspace) : problem(404, 'MSG60'))
    );
  }),
  http.put('*/api/v1/workspace', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accessError();
    if (denied) return denied;
    if (!workspace) return problem(404, 'MSG60');
    const input: unknown = await request.json().catch(() => null);
    if (!isRecord(input)) return problem(400);
    const invalid = validate(input, false);
    if (invalid) return invalid;
    if (typeof input.companyName !== 'string' || typeof input.serviceAccountEmail !== 'string')
      return problem(400);
    workspace = {
      ...workspace,
      companyName: input.companyName.trim(),
      serviceAccountEmail: input.serviceAccountEmail.trim(),
    };
    return HttpResponse.json<WorkspaceInfo>(workspace);
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  workspace = undefined;
}
