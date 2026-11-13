import { createProject, listProjects, testProjectConnection, type ProjectList } from '@/shared/api';
import { server } from '@/mocks/server';
import { setDevUser } from '@/shared/auth';
import { MOCK_PROJECT_IDS, resetMockData } from './handlers';

/** The HTTP status of the next Save response (the generated functions return only the body). */
function nextSaveStatus() {
  return new Promise<number>((resolve) => {
    server.events.on('response:mocked', ({ request, response }) => {
      if (request.method === 'POST' && new URL(request.url).pathname === '/api/v1/projects') {
        resolve(response.status);
      }
    });
  });
}

const keys = (list: ProjectList) => list.projects.map((p) => p.jiraProjectKey);

describe('jira-integration mock contract', () => {
  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-005.1 lists only the own Department Projects, sorted by key, with typed rows', async () => {
    const list = await listProjects();
    expect(list.department.name).toBe('IT Operations');
    expect(keys(list)).toEqual(['OLD', 'OPS']);
    expect(list.projects[0]).toEqual({
      projectId: MOCK_PROJECT_IDS.OLD,
      jiraProjectKey: 'OLD',
      projectManager: { userId: '00000000-0000-4000-8000-000000000003', fullName: 'Đỗ Minh Quân' },
      memberCount: 2,
      status: 'REMOVED',
    });
  });

  it('AC-005.1 refuses a signed-out caller and a caller without the Project Manager role', async () => {
    setDevUser(null);
    await expect(listProjects()).rejects.toMatchObject({ status: 401 });
    setDevUser('admin');
    await expect(listProjects()).rejects.toMatchObject({ status: 403, code: 'MSG08' });
  });

  it('Add project fixture: tests a new padded lowercase key with Done-category defaults and range 3', async () => {
    const result = await testProjectConnection({ jiraProjectKey: '  new_one ' });
    expect(result).toMatchObject({
      jiraProjectKey: 'NEW_ONE',
      reconnect: false,
      doneStatusIds: ['10002', '10003'],
      reviewRange: 3,
    });
    expect(result).not.toHaveProperty('projectId');
    expect(result.statuses.some((s) => !s.done)).toBe(true);
  });

  it('Add project fixture: saves a new Project with 201 and the next list shows it', async () => {
    const status = nextSaveStatus();
    const saved = await createProject({
      jiraProjectKey: 'new',
      doneStatusIds: ['10001', '10002', '10001'],
      reviewRange: 5,
    });
    expect(await status).toBe(201);
    expect(saved).toMatchObject({
      jiraProjectKey: 'NEW',
      doneStatusIds: ['10001', '10002'],
      reviewRange: 5,
      status: 'ACTIVE',
    });
    const list = await listProjects();
    expect(keys(list)).toEqual(['NEW', 'OLD', 'OPS']);
    expect(list.projects[0]).toMatchObject({ projectId: saved.projectId, memberCount: 1 });
  });

  it('Add project fixture: reconnects OLD with 200, keeping its id and member count, after a prefill of its kept settings', async () => {
    expect(await testProjectConnection({ jiraProjectKey: 'old' })).toMatchObject({
      jiraProjectKey: 'OLD',
      reconnect: true,
      projectId: MOCK_PROJECT_IDS.OLD,
      doneStatusIds: ['10003'],
      reviewRange: 7,
    });
    const status = nextSaveStatus();
    const saved = await createProject({
      jiraProjectKey: 'OLD',
      doneStatusIds: ['10002'],
      reviewRange: 10,
    });
    expect(await status).toBe(200);
    expect(saved).toMatchObject({ projectId: MOCK_PROJECT_IDS.OLD, status: 'ACTIVE' });
    const old = (await listProjects()).projects.find((p) => p.jiraProjectKey === 'OLD');
    expect(old).toMatchObject({
      projectId: MOCK_PROJECT_IDS.OLD,
      memberCount: 2,
      status: 'ACTIVE',
    });
  });

  it('Add project fixture: answers MSG49 on jiraProjectKey for an Active key, also of another Department', async () => {
    for (const jiraProjectKey of ['sup', 'OPS']) {
      const department_name = jiraProjectKey === 'sup' ? 'Customer Support' : 'IT Operations';
      const expected = {
        status: 409,
        code: 'MSG49',
        fieldErrors: [
          {
            field: 'jiraProjectKey',
            code: 'MSG49',
            params: { tracker_name: 'Jira', department_name },
          },
        ],
      };
      await expect(testProjectConnection({ jiraProjectKey })).rejects.toMatchObject(expected);
      await expect(
        createProject({ jiraProjectKey, doneStatusIds: ['10002'], reviewRange: 3 }),
      ).rejects.toMatchObject(expected);
    }
  });

  it('Add project fixture: answers MSG48 without a field for MISSING and for a key Jira cannot have', async () => {
    for (const jiraProjectKey of ['missing', '1BAD']) {
      const expected = { status: 409, code: 'MSG48', params: { tracker_name: 'Jira' } };
      await expect(testProjectConnection({ jiraProjectKey })).rejects.toMatchObject({
        ...expected,
        fieldErrors: [],
      });
      await expect(
        createProject({ jiraProjectKey, doneStatusIds: ['10002'], reviewRange: 3 }),
      ).rejects.toMatchObject(expected);
    }
  });

  it('Add project fixture: answers the field errors of the request checks', async () => {
    const field = (name: string, code: string) => ({
      status: 400,
      fieldErrors: [expect.objectContaining({ field: name, code })],
    });
    await expect(testProjectConnection({ jiraProjectKey: '   ' })).rejects.toMatchObject(
      field('jiraProjectKey', 'MSG01'),
    );
    await expect(testProjectConnection({ jiraProjectKey: 'A'.repeat(21) })).rejects.toMatchObject(
      field('jiraProjectKey', 'MSG02'),
    );
    const save = (body: { doneStatusIds: string[]; reviewRange: number }) =>
      createProject({ jiraProjectKey: 'NEW', ...body });
    await expect(save({ doneStatusIds: [], reviewRange: 3 })).rejects.toMatchObject(
      field('doneStatusIds', 'MSG01'),
    );
    await expect(save({ doneStatusIds: ['77777'], reviewRange: 3 })).rejects.toMatchObject(
      field('doneStatusIds', 'MSG01'),
    );
    for (const reviewRange of [0, -1, 2.5, 2147483648]) {
      await expect(save({ doneStatusIds: ['10002'], reviewRange })).rejects.toMatchObject(
        field('reviewRange', 'MSG44'),
      );
    }
    expect(keys(await listProjects())).toEqual(['OLD', 'OPS']);
  });

  it('Add project fixture: resetMockData restores the seeded ids, settings and counts', async () => {
    const first = await createProject({
      jiraProjectKey: 'NEW',
      doneStatusIds: ['10002'],
      reviewRange: 3,
    });
    await createProject({ jiraProjectKey: 'OLD', doneStatusIds: ['10002'], reviewRange: 9 });
    resetMockData();

    const list = await listProjects();
    expect(keys(list)).toEqual(['OLD', 'OPS']);
    expect(list.projects[0]).toMatchObject({ status: 'REMOVED', memberCount: 2 });
    expect(await testProjectConnection({ jiraProjectKey: 'OLD' })).toMatchObject({
      doneStatusIds: ['10003'],
      reviewRange: 7,
    });
    const again = await createProject({
      jiraProjectKey: 'NEW',
      doneStatusIds: ['10002'],
      reviewRange: 3,
    });
    expect(again.projectId).toBe(first.projectId);
  });
});
