import {
  getMySolutionSummary,
  getMySolutions,
  getMyViewableSpaces,
  isApiError,
} from '@/shared/api';
import { setDevUser, type DevUserId } from '@/shared/auth';
import { MY_SOLUTIONS } from './data';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

const VIEWABLE_COUNTS: [DevUserId, number][] = [
  ['employee', 0],
  ['tr', 3],
  ['tr-two-departments', 4],
  ['pm', 2],
  ['km', 3],
  ['admin', 1],
  ['pm-km', 2],
  ['admin-tr', 6],
];

async function rejection(call: () => Promise<unknown>) {
  try {
    await call();
  } catch (error) {
    if (isApiError(error)) return error;
  }
  throw new Error('expected an ApiError');
}

describe('my-profile mock contract', () => {
  it.each(VIEWABLE_COUNTS)(
    "contract mirror of AC-003.4 answers %s's Spaces sorted by name in the SpaceRef shape (%i)",
    async (user, count) => {
      setDevUser(user);
      const spaces = await getMyViewableSpaces();
      expect(spaces).toHaveLength(count);
      const names = spaces.map((space) => space.name);
      expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b, 'vi')));
      for (const space of spaces) {
        expect(Object.keys(space).sort()).toEqual(['name', 'spaceId']);
        expect(space.spaceId).toMatch(UUID);
      }
      expect(new Set(spaces.map((space) => space.spaceId)).size).toBe(count);
      if (user === 'km') expect(names).toContain('IT Space');
      if (user === 'pm-km') expect(names).toContain('HR Space');
      if (user === 'admin-tr') expect(names.some((name) => name.length === 120)).toBe(true);
    },
  );

  it('contract mirror of AC-003.5 answers 200 with an empty list for a user without viewable Spaces', async () => {
    setDevUser('employee');
    await expect(getMyViewableSpaces()).resolves.toEqual([]);
  });

  it('contract mirror of AC-003.10 answers 401 MSG07 when no dev user is signed in', async () => {
    setDevUser(null);
    const error = await rejection(() => getMyViewableSpaces());
    expect(error).toMatchObject({ status: 401, code: 'MSG07' });
  });

  it('contract mirror of AC-003.4 ignores userId and workspaceId query parameters', async () => {
    setDevUser('km');
    const kmSpaces = await getMyViewableSpaces();
    setDevUser('tr');
    const own = await getMyViewableSpaces();
    const response = await fetch(
      `/api/v1/me/spaces?userId=${kmSpaces[0]?.spaceId}&workspaceId=00000000-0000-4000-8000-0000000000ff`,
      { credentials: 'include' },
    );
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual(own);
    expect(own).not.toEqual(kmSpaces);
  });
});

const STATUSES = [
  'PENDING_REVIEW',
  'CHANGES_REQUESTED',
  'PUBLISHED',
  'REJECTED',
  'UNPUBLISHED',
  'EXPIRED',
] as const;
const DECISIONS = ['APPROVE', 'REQUEST_CHANGES', 'REJECT'];
const ROW_KEYS = [
  'canOpen',
  'latestDecision',
  'projectId',
  'projectName',
  'status',
  'ticketId',
  'ticketKey',
  'title',
  'updatedAt',
];
const FOREIGN_PROJECT = '00000000-0000-4000-8000-0000000000aa';

async function allRows(user: DevUserId) {
  setDevUser(user);
  return (await getMySolutions({ size: 100 })).content;
}

/** The calendar day of an instant in UTC+7 (AC-003.19). */
function dayInUtc7(iso: string) {
  return new Date(Date.parse(iso) + 7 * 3_600_000).toISOString().slice(0, 10);
}

async function rawGet(query: string) {
  const response = await fetch(`/api/v1/me/solutions${query}`, { credentials: 'include' });
  return { status: response.status, body: (await response.json()) as Record<string, unknown> };
}

function fieldNames(body: Record<string, unknown>) {
  const errors = body.fieldErrors as { field: string; code: string }[];
  return errors.map((error) => error.field).sort();
}

describe('my-solutions mock contract', () => {
  it('contract mirror of AC-003.6 answers tr first page: 20 rows, totals 45, newest updatedAt first', async () => {
    setDevUser('tr');
    const result = await getMySolutions();
    expect(result.content).toHaveLength(20);
    expect(result.page).toMatchObject({ number: 0, size: 20, totalElements: 45, totalPages: 3 });
    const times = result.content.map((row) => Date.parse(row.updatedAt));
    expect(times).toEqual([...times].sort((a, b) => b - a));
  });

  it('edge 1 rows with the same updatedAt are ordered by ticket id ascending', async () => {
    const rows = await allRows('tr');
    const groups = new Map<string, string[]>();
    for (const row of rows) {
      groups.set(row.updatedAt, [...(groups.get(row.updatedAt) ?? []), row.ticketId]);
    }
    const ties = [...groups.values()].filter((ids) => ids.length > 1);
    expect(ties.length).toBeGreaterThan(0);
    for (const ids of ties) expect(ids).toEqual([...ids].sort());
  });

  it('contract mirror of AC-003.6 rows have the MySolution shape incl. projectId and required nullable latestDecision', async () => {
    const rows = await allRows('tr');
    expect(rows).toHaveLength(45);
    for (const row of rows) {
      expect(Object.keys(row).sort()).toEqual(ROW_KEYS);
      expect(row.projectId).toMatch(UUID);
      expect(row.ticketId).toMatch(UUID);
      expect('latestDecision' in row).toBe(true);
      if (row.latestDecision !== null) {
        expect(DECISIONS).toContain(row.latestDecision.action);
        expect(['string', 'object']).toContain(typeof row.latestDecision.comment);
        expect(Number.isNaN(Date.parse(row.latestDecision.decidedAt))).toBe(false);
      }
    }
  });

  it('contract mirror of AC-003.6 defaults to page 0 size 20 and honours page and size', async () => {
    setDevUser('tr');
    const first = await getMySolutions();
    expect(first.content).toHaveLength(20);
    expect(first.page.number).toBe(0);
    const full = await getMySolutions({ size: 100 });
    expect(full.content).toHaveLength(45);
    const slice = await getMySolutions({ page: 1, size: 10 });
    expect(slice.content).toEqual(full.content.slice(10, 20));
  });

  it('contract mirror of AC-003.6 fixture covers all six statuses, Vietnamese and 200-character titles, rows with canOpen false, several Projects, decisions with and without comment', async () => {
    const rows = await allRows('tr');
    expect(rows).toHaveLength(45);
    expect(new Set(rows.map((row) => row.status))).toEqual(new Set(STATUSES));
    expect(rows.some((row) => /[ăâđêôơưáàảãạéèíìóòúùýỳ]/i.test(row.title))).toBe(true);
    expect(rows.some((row) => row.title.length === 200)).toBe(true);
    expect(rows.some((row) => !row.canOpen)).toBe(true);
    expect(rows.some((row) => row.canOpen)).toBe(true);
    expect(new Set(rows.map((row) => row.projectId)).size).toBeGreaterThan(1);
    expect(rows.some((row) => row.latestDecision?.comment)).toBe(true);
    expect(rows.some((row) => row.latestDecision && row.latestDecision.comment === null)).toBe(
      true,
    );
    expect(rows.some((row) => row.latestDecision === null)).toBe(true);
  });

  it('contract mirror of AC-003.6 pages of 45 rows: 20, 20, 5', async () => {
    setDevUser('tr');
    const pages = await Promise.all([0, 1, 2].map((page) => getMySolutions({ page })));
    expect(pages.map((p) => p.content.length)).toEqual([20, 20, 5]);
    const ids = pages.flatMap((p) => p.content.map((row) => row.ticketId));
    expect(new Set(ids).size).toBe(45);
  });

  it('contract mirror of AC-003.6 past the end returns empty content with the real totals', async () => {
    setDevUser('tr');
    const result = await getMySolutions({ page: 3 });
    expect(result.content).toEqual([]);
    expect(result.page).toMatchObject({ totalElements: 45, totalPages: 3 });
  });

  it.each(['tr-two-departments', 'admin-tr'] as DevUserId[])(
    'contract mirror of AC-003.8 resolver %s with 0 Solutions gets 200 and an empty page',
    async (user) => {
      setDevUser(user);
      const result = await getMySolutions();
      expect(result.content).toEqual([]);
      expect(result.page.totalElements).toBe(0);
    },
  );

  it('contract mirror of AC-003.7 ignores userId and workspaceId query parameters', async () => {
    setDevUser('tr');
    const own = await getMySolutions();
    const { status, body } = await rawGet(
      `?userId=${FOREIGN_PROJECT}&workspaceId=00000000-0000-4000-8000-0000000000ff`,
    );
    expect(status).toBe(200);
    expect(body).toEqual(own);
    setDevUser('pm');
    expect(await getMySolutions()).not.toEqual(own);
  });

  it('contract mirror of AC-003.9 page=-1 gives 400 MSG06 on page', async () => {
    setDevUser('tr');
    const error = await rejection(() => getMySolutions({ page: -1 }));
    expect(error).toMatchObject({ status: 400, code: 'MSG06' });
    expect(error.fieldErrors).toEqual([expect.objectContaining({ field: 'page', code: 'MSG06' })]);
  });

  it.each(['0', '101', 'abc'])(
    'contract mirror of AC-003.9 size=%s gives 400 MSG06 on size',
    async (size) => {
      setDevUser('tr');
      const { status, body } = await rawGet(`?size=${size}`);
      expect(status).toBe(400);
      expect(body.fieldErrors).toEqual([expect.objectContaining({ field: 'size', code: 'MSG06' })]);
    },
  );

  it.each([
    ['status=DONE', 'status'],
    ['projectId=abc', 'projectId'],
    ['from=31-10-2026', 'from'],
    ['to=2026-13-40', 'to'],
  ])('contract mirror of AC-003.9 %s gives 400 MSG06 on %s', async (query, field) => {
    setDevUser('tr');
    const { status, body } = await rawGet(`?${query}`);
    expect(status).toBe(400);
    expect(body.fieldErrors).toEqual([expect.objectContaining({ field, code: 'MSG06' })]);
  });

  it('contract mirror of AC-003.9 to before from gives 400 MSG06 on to', async () => {
    setDevUser('tr');
    const { status, body } = await rawGet('?from=2026-10-31&to=2026-10-30');
    expect(status).toBe(400);
    expect(fieldNames(body)).toEqual(['to']);
    expect(body.fieldErrors).toEqual([expect.objectContaining({ code: 'MSG06' })]);
  });

  it('contract mirror of AC-003.9 several wrong parameters give one fieldErrors entry each', async () => {
    setDevUser('tr');
    const { status, body } = await rawGet('?page=-1&size=0');
    expect(status).toBe(400);
    expect(fieldNames(body)).toEqual(['page', 'size']);
  });

  it('contract mirror of AC-003.10 answers 401 MSG07 when nobody is signed in, before the 403 check', async () => {
    setDevUser(null);
    const error = await rejection(() => getMySolutions());
    expect(error).toMatchObject({ status: 401, code: 'MSG07' });
  });

  it.each(['employee', 'km', 'admin'] as DevUserId[])(
    'contract mirror of AC-003.17 %s without Ticket Resolver or Project Manager gets 403 MSG08',
    async (user) => {
      setDevUser(user);
      const error = await rejection(() => getMySolutions());
      expect(error).toMatchObject({ status: 403, code: 'MSG08' });
    },
  );

  it.each(['tr', 'pm', 'pm-km', 'tr-two-departments', 'admin-tr'] as DevUserId[])(
    'contract mirror of AC-003.17 %s with Ticket Resolver or Project Manager gets 200',
    async (user) => {
      setDevUser(user);
      const rows = await allRows(user);
      if (user === 'tr') expect(rows).toHaveLength(45);
      if (user === 'pm') expect(rows.length).toBeGreaterThan(0);
      if (user === 'pm-km') expect(rows).toEqual(await allRows('pm'));
      if (user === 'tr-two-departments' || user === 'admin-tr') expect(rows).toEqual([]);
    },
  );

  it.each(STATUSES)(
    'contract mirror of AC-003.19 status=%s returns only that status, with matching totals',
    async (status) => {
      const full = await allRows('tr');
      const result = await getMySolutions({ status, size: 100 });
      expect(result.content.length).toBeGreaterThan(0);
      expect(result.content.every((row) => row.status === status)).toBe(true);
      expect(result.page.totalElements).toBe(full.filter((row) => row.status === status).length);
    },
  );

  it('contract mirror of AC-003.19 projectId filter returns only that Project, and a foreign projectId gives an empty page', async () => {
    const full = await allRows('tr');
    const projectId = full[0]?.projectId ?? '';
    const result = await getMySolutions({ projectId, size: 100 });
    expect(result.content.every((row) => row.projectId === projectId)).toBe(true);
    expect(result.page.totalElements).toBe(full.filter((r) => r.projectId === projectId).length);
    const foreign = await getMySolutions({ projectId: FOREIGN_PROJECT });
    expect(foreign.content).toEqual([]);
    expect(foreign.page.totalElements).toBe(0);
  });

  it('contract mirror of AC-003.19 from and to are inclusive whole days in UTC+7, combined with AND', async () => {
    const full = await allRows('tr');
    const times = full.map((row) => row.updatedAt);
    expect(times).toContain('2026-10-30T16:59:59Z');
    expect(times).toContain('2026-10-30T17:00:00Z');
    const day = async (from: string, to: string) =>
      (await getMySolutions({ from, to, size: 100 })).content;
    const on30 = await day('2026-10-30', '2026-10-30');
    const on31 = await day('2026-10-31', '2026-10-31');
    expect(on30.map((r) => r.updatedAt)).toContain('2026-10-30T16:59:59Z');
    expect(on30.map((r) => r.updatedAt)).not.toContain('2026-10-30T17:00:00Z');
    expect(on31.map((r) => r.updatedAt)).toContain('2026-10-30T17:00:00Z');
    expect(on31.map((r) => r.updatedAt)).not.toContain('2026-10-30T16:59:59Z');
    const both = await day('2026-10-30', '2026-10-31');
    expect(both).toEqual(
      full.filter((r) => ['2026-10-30', '2026-10-31'].includes(dayInUtc7(r.updatedAt))),
    );
    const projectId = on30[0]?.projectId ?? '';
    const combined = await getMySolutions({
      status: on30[0]?.status,
      projectId,
      from: '2026-10-30',
      to: '2026-10-30',
      size: 100,
    });
    expect(combined.content).toEqual(
      full.filter(
        (r) =>
          r.status === on30[0]?.status &&
          r.projectId === projectId &&
          dayInUtc7(r.updatedAt) === '2026-10-30',
      ),
    );
  });

  it('contract mirror of AC-003.19 extra parameters (sort, foo) are ignored', async () => {
    setDevUser('tr');
    const plain = await getMySolutions();
    const { status, body } = await rawGet('?sort=title,asc&foo=bar');
    expect(status).toBe(200);
    expect(body).toEqual(plain);
  });
});

const SUMMARY_URL = '/api/v1/me/solutions/summary';

function countsOf(user: DevUserId) {
  const rows = MY_SOLUTIONS[user] ?? [];
  return Object.fromEntries(
    STATUSES.map((status) => [status, rows.filter((row) => row.status === status).length]),
  );
}

describe('my-solutions summary mock contract', () => {
  it("contract mirror of AC-003.18 tr's counts have all six statuses, each the number of tr's Solutions in that status", async () => {
    setDevUser('tr');
    const { counts } = await getMySolutionSummary();
    expect(Object.keys(counts).sort()).toEqual([...STATUSES].sort());
    expect(counts).toEqual(countsOf('tr'));
    for (const status of STATUSES) {
      expect(counts[status]).toBeGreaterThan(0);
      const page = await getMySolutions({ status, size: 100 });
      expect(page.page.totalElements).toBe(counts[status]);
    }
    expect(Object.values(counts).reduce((a, b) => a + b, 0)).toBe(45);
  });

  it("contract mirror of AC-003.18 projects are the distinct Projects of tr's Solutions as ProjectRef, sorted by name", async () => {
    setDevUser('tr');
    const { projects } = await getMySolutionSummary();
    for (const project of projects) {
      expect(Object.keys(project).sort()).toEqual(['name', 'projectId']);
      expect(project.projectId).toMatch(UUID);
    }
    expect(new Set(projects.map((p) => p.projectId)).size).toBe(projects.length);
    const expected = new Map((MY_SOLUTIONS.tr ?? []).map((r) => [r.projectId, r.projectName]));
    expect(new Map(projects.map((p) => [p.projectId, p.name]))).toEqual(expected);
    const names = projects.map((p) => p.name);
    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b)));
  });

  it('contract mirror of AC-003.18 ignores every filter and paging parameter', async () => {
    setDevUser('tr');
    const plain = await getMySolutionSummary();
    const projectId = MY_SOLUTIONS.tr?.[0]?.projectId ?? '';
    const response = await fetch(
      `${SUMMARY_URL}?status=EXPIRED&projectId=${projectId}&from=2026-10-01&to=2026-10-02&page=2&size=5`,
      { credentials: 'include' },
    );
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual(plain);
  });

  it.each(['pm', 'pm-km'] as DevUserId[])(
    'contract mirror of AC-003.18 Project Manager %s gets the counts and Projects of their own Solutions',
    async (user) => {
      setDevUser(user);
      const { counts, projects } = await getMySolutionSummary();
      expect(Object.values(counts).reduce((a, b) => a + b, 0)).toBe(8);
      expect(counts).toEqual(countsOf(user));
      expect(Object.keys(counts).sort()).toEqual([...STATUSES].sort());
      expect(projects.length).toBeGreaterThan(0);
    },
  );

  it.each(['tr-two-departments', 'admin-tr'] as DevUserId[])(
    'contract mirror of AC-003.8 resolver %s with 0 Solutions gets six 0 counts and no Projects',
    async (user) => {
      setDevUser(user);
      const summary = await getMySolutionSummary();
      expect(Object.keys(summary.counts).sort()).toEqual([...STATUSES].sort());
      expect(Object.values(summary.counts)).toEqual([0, 0, 0, 0, 0, 0]);
      expect(summary.projects).toEqual([]);
    },
  );

  it('contract mirror of AC-003.10 the summary answers 401 MSG07 when nobody is signed in, before the role check', async () => {
    setDevUser(null);
    const error = await rejection(() => getMySolutionSummary());
    expect(error).toMatchObject({ status: 401, code: 'MSG07' });
  });

  it.each(['employee', 'km', 'admin'] as DevUserId[])(
    'contract mirror of AC-003.17 the summary answers 403 MSG08 for %s without Ticket Resolver or Project Manager',
    async (user) => {
      setDevUser(user);
      const error = await rejection(() => getMySolutionSummary());
      expect(error).toMatchObject({ status: 403, code: 'MSG08' });
    },
  );
});
