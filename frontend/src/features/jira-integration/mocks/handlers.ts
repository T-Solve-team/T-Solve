import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import {
  getMe,
  isApiError,
  type CurrentUser,
  type DepartmentRef,
  type Project,
  type ProjectConnectionTest,
  type ProjectList,
  type ProjectManagerRef,
  type ProjectRow,
  type TrackerStatus,
} from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';

/**
 * MSW mock handlers of the "jira-integration" feature (guideline 10 §5): the Projects of screen 3.1 and the
 * Add project popup's connection test and Save, with the backend's checks in its order (spec STORY-001–004).
 */

/** Public ids of the seeded Projects. */
export const MOCK_PROJECT_IDS = {
  OPS: '00000000-0000-4000-8000-0000000003a1',
  OLD: '00000000-0000-4000-8000-0000000003a2',
  SUP: '00000000-0000-4000-8000-0000000003a3',
} as const;

/** Every mock Project Manager belongs to this Department. */
const OWN: DepartmentRef = {
  departmentId: '00000000-0000-4000-8000-0000000003d1',
  name: 'IT Operations',
};
const SUPPORT: DepartmentRef = {
  departmentId: '00000000-0000-4000-8000-0000000003d2',
  name: 'Customer Support',
};
/** The "pm" fixture user (src/mocks/session.ts), so the default mock user reconnects OLD as its previous PM. */
const QUAN: ProjectManagerRef = {
  userId: '00000000-0000-4000-8000-000000000003',
  fullName: 'Đỗ Minh Quân',
};

/** Every mock Jira project has these statuses: two Done-category and two other choices. */
const JIRA_STATUSES: TrackerStatus[] = [
  { id: '10000', name: 'To Do', done: false },
  { id: '10001', name: 'In Progress', done: false },
  { id: '10002', name: 'Done', done: true },
  { id: '10003', name: 'Closed', done: true },
];
/** The key mock Jira does not find, or the service account cannot browse (MSG48). */
const MISSING_KEY = 'MISSING';
const KEY_FORMAT = /^[A-Z][A-Z0-9_]*$/;
const MAX_KEY_LENGTH = 20;
const MAX_REVIEW_RANGE = 2_147_483_647;

type MockProject = Omit<ProjectRow, 'projectManager'> & {
  department: DepartmentRef;
  projectManager: ProjectManagerRef | null;
  doneStatusIds: string[];
  reviewRange: number | null;
};

/** Fresh objects on every call, so a reset never shares state with an earlier test. */
function seedProjects(): MockProject[] {
  return [
    {
      projectId: MOCK_PROJECT_IDS.OPS,
      jiraProjectKey: 'OPS',
      department: OWN,
      projectManager: QUAN,
      memberCount: 3,
      status: 'ACTIVE',
      doneStatusIds: ['10002'],
      reviewRange: 3,
    },
    {
      // Removed: its stored '99999' is no longer in Jira, so the prefill keeps only '10003'.
      projectId: MOCK_PROJECT_IDS.OLD,
      jiraProjectKey: 'OLD',
      department: OWN,
      projectManager: QUAN,
      memberCount: 2,
      status: 'REMOVED',
      doneStatusIds: ['10003', '99999'],
      reviewRange: 7,
    },
    {
      projectId: MOCK_PROJECT_IDS.SUP,
      jiraProjectKey: 'SUP',
      department: SUPPORT,
      projectManager: { userId: '00000000-0000-4000-8000-0000000003f1', fullName: 'Lê Văn Hùng' },
      memberCount: 4,
      status: 'ACTIVE',
      doneStatusIds: ['10002'],
      reviewRange: 5,
    },
  ];
}

const store = { projects: seedProjects(), created: 0 };

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  store.projects = seedProjects();
  store.created = 0;
}

type Refusal = ReturnType<typeof problem>;

async function projectManager(): Promise<CurrentUser | Refusal> {
  try {
    const user = await getMe();
    return user.roles.includes('PROJECT_MANAGER') ? user : problem(403, 'MSG08');
  } catch (error) {
    return isApiError(error) ? problem(error.status, error.code) : problem(401, 'MSG07');
  }
}

async function readBody(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const body: unknown = await request.json();
    return body !== null && typeof body === 'object' && !Array.isArray(body)
      ? (body as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

function fieldError(
  status: number,
  field: string,
  code: 'MSG01' | 'MSG02' | 'MSG44' | 'MSG49',
  params = {},
) {
  return problem(status, code, { params, fieldErrors: [{ field, code, params }] });
}

/** Checks the key like the backend (AC-001.2–AC-001.7) and answers the normalised key or a refusal. */
function checkKey(raw: unknown, user: CurrentUser): { key: string; held?: MockProject } | Refusal {
  const params = { field_name: 'jiraProjectKey' };
  if (typeof raw !== 'string' || raw.trim() === '')
    return fieldError(400, 'jiraProjectKey', 'MSG01', params);
  if (raw.length > MAX_KEY_LENGTH) {
    return fieldError(400, 'jiraProjectKey', 'MSG02', { ...params, max_length: MAX_KEY_LENGTH });
  }
  const key = raw.trim().toUpperCase();
  if (!KEY_FORMAT.test(key)) return problem(409, 'MSG48', { params: { tracker_name: 'Jira' } });
  const held = store.projects.find((p) => p.jiraProjectKey === key);
  if (!held) return { key };
  const otherManager = held.projectManager !== null && held.projectManager.userId !== user.user.id;
  if (
    held.status === 'ACTIVE' ||
    held.department.departmentId !== OWN.departmentId ||
    otherManager
  ) {
    return fieldError(409, 'jiraProjectKey', 'MSG49', {
      tracker_name: 'Jira',
      department_name: held.department.name,
    });
  }
  return { key, held };
}

const doneDefaults = () => JIRA_STATUSES.filter((s) => s.done).map((s) => s.id);
const inJira = (id: string) => JIRA_STATUSES.some((s) => s.id === id);

function toRow({
  projectId,
  jiraProjectKey,
  projectManager,
  memberCount,
  status,
}: MockProject): ProjectRow {
  return { projectId, jiraProjectKey, projectManager, memberCount, status };
}

/** Checks the Save settings (AC-002.4–AC-002.7, AC-002.16) and answers the unique ids and the range. */
function checkSettings(body: Record<string, unknown>): { ids: string[]; range: number } | Refusal {
  const { doneStatusIds: ids, reviewRange: range } = body;
  if (ids !== undefined && ids !== null && !Array.isArray(ids)) return problem(400, 'MSG06');
  if (range !== undefined && range !== null && typeof range !== 'number')
    return problem(400, 'MSG06');
  const statusesParams = { field_name: 'doneStatusIds' };
  if (!Array.isArray(ids) || ids.length === 0)
    return fieldError(400, 'doneStatusIds', 'MSG01', statusesParams);
  if (typeof range !== 'number')
    return fieldError(400, 'reviewRange', 'MSG01', { field_name: 'reviewRange' });
  if (!Number.isInteger(range) || range < 1 || range > MAX_REVIEW_RANGE) {
    return fieldError(400, 'reviewRange', 'MSG44');
  }
  if (!ids.every((id): id is string => typeof id === 'string' && id.trim() !== '')) {
    return fieldError(400, 'doneStatusIds', 'MSG01', statusesParams);
  }
  return { ids: [...new Set(ids)], range };
}

export const handlers: HttpHandler[] = [
  http.get('*/api/v1/projects', async () => {
    await delay(MOCK_DELAY_MS);
    const user = await projectManager();
    if (user instanceof Response) return user;
    const own = store.projects
      .filter((p) => p.department.departmentId === OWN.departmentId)
      .sort((a, b) => (a.jiraProjectKey < b.jiraProjectKey ? -1 : 1));
    return HttpResponse.json<ProjectList>({ department: OWN, projects: own.map(toRow) });
  }),

  http.post('*/api/v1/projects/connection-test', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const user = await projectManager();
    if (user instanceof Response) return user;
    const body = await readBody(request);
    if (!body) return problem(400, 'MSG06');
    const checked = checkKey(body.jiraProjectKey, user);
    if (checked instanceof Response) return checked;
    if (checked.key === MISSING_KEY)
      return problem(409, 'MSG48', { params: { tracker_name: 'Jira' } });
    const { key, held } = checked;
    const kept = held?.doneStatusIds.filter(inJira) ?? [];
    return HttpResponse.json<ProjectConnectionTest>({
      jiraProjectKey: key,
      statuses: JIRA_STATUSES,
      reconnect: held !== undefined,
      ...(held && { projectId: held.projectId }),
      doneStatusIds: kept.length > 0 ? kept : doneDefaults(),
      reviewRange: held?.reviewRange ?? 3,
    });
  }),

  http.post('*/api/v1/projects', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const user = await projectManager();
    if (user instanceof Response) return user;
    const body = await readBody(request);
    if (!body) return problem(400, 'MSG06');
    const settings = checkSettings(body);
    if (settings instanceof Response) return settings;
    const checked = checkKey(body.jiraProjectKey, user);
    if (checked instanceof Response) return checked;
    if (checked.key === MISSING_KEY)
      return problem(409, 'MSG48', { params: { tracker_name: 'Jira' } });
    if (!settings.ids.every(inJira)) {
      return fieldError(400, 'doneStatusIds', 'MSG01', { field_name: 'doneStatusIds' });
    }
    const { key, held } = checked;
    const saved = {
      jiraProjectKey: key,
      status: 'ACTIVE' as const,
      doneStatusIds: settings.ids,
      reviewRange: settings.range,
    };
    const caller = { userId: user.user.id, fullName: user.user.fullName };
    let projectId: string;
    if (held) {
      // Reconnect (AC-003.1–AC-003.4): same id and members; a Project without a manager gets the caller.
      projectId = held.projectId;
      if (held.projectManager === null) held.memberCount += 1;
      Object.assign(held, saved, { projectManager: caller });
    } else {
      projectId = `00000000-0000-4000-8000-${String(0x400 + store.created).padStart(12, '0')}`;
      store.created += 1;
      store.projects.push({
        projectId,
        department: OWN,
        projectManager: caller,
        memberCount: 1,
        ...saved,
      });
    }
    return HttpResponse.json<Project>(
      { projectId, departmentId: OWN.departmentId, ...saved },
      { status: held ? 200 : 201 },
    );
  }),
];
