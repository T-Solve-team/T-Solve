import {
  createUser,
  getMe,
  getUserDetail,
  getUserHandoverOptions,
  getUsers,
  updateUserRoles,
  updateUserStatus,
  type UpdateUserRolesRequest,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { store } from './state';

const id = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const IT = '00000000-0000-4000-8000-0000000000d1';
const HR = '00000000-0000-4000-8000-0000000000d2';
const IT_SPACE = '00000000-0000-4000-8000-0000000000a1';
const HR_SPACE = '00000000-0000-4000-8000-0000000000a2';
const HANDBOOK = '00000000-0000-4000-8000-0000000000a3';
const EMPLOYEE: UpdateUserRolesRequest = { roles: [], departmentId: null, spaceIds: [] };

describe('Workspace role and assignment contract', () => {
  beforeEach(() => setDevUser('admin'));

  it('AC-007.1 returns role assignments and actual removable Ticket Resolver memberships', async () => {
    const detail = await getUserDetail(id(4));
    expect(detail.roles).toContain('TICKET_RESOLVER');
    expect(detail.ticketResolverProjectIds).toHaveLength(4);
    expect(new Set(detail.ticketResolverProjectIds)).toEqual(
      new Set(detail.projects.map((project) => project.projectId)),
    );
    expect((await getUserDetail(id(5))).ticketResolverProjectIds).toEqual([]);
  });

  it('AC-007.2 retains Employee and immutable identity/status while updating assignments', async () => {
    const before = await getUserDetail(id(2));
    const after = await updateUserRoles(id(2), EMPLOYEE);
    expect(after.roles).toEqual(['EMPLOYEE']);
    expect(after.department).toBeNull();
    expect(after.managedSpaces).toEqual([]);
    expect(after).toMatchObject({
      id: before.id,
      email: before.email,
      fullName: before.fullName,
      status: before.status,
    });
  });

  it.each([
    null,
    {},
    { roles: 'ADMIN', departmentId: null, spaceIds: [] },
    { roles: ['OWNER'], departmentId: null, spaceIds: [] },
    { roles: ['PROJECT_MANAGER'], departmentId: null, spaceIds: [] },
    { roles: [], departmentId: 'foreign', spaceIds: [] },
    { roles: ['KNOWLEDGE_MANAGER'], departmentId: null, spaceIds: [] },
    { roles: [], departmentId: null, spaceIds: [HANDBOOK] },
    { roles: [], departmentId: null, spaceIds: null },
    { roles: ['KNOWLEDGE_MANAGER'], departmentId: null, spaceIds: [42] },
    { roles: ['KNOWLEDGE_MANAGER'], departmentId: null, spaceIds: ['unknown'] },
    { roles: ['KNOWLEDGE_MANAGER'], departmentId: null, spaceIds: [HANDBOOK, HANDBOOK] },
    { roles: [], departmentId: null, spaceIds: [], confirmTicketResolverRemoval: 'true' },
  ])('AC-007.2 rejects invalid role or assignment JSON atomically: %j', async (body) => {
    const before = await getUserDetail(id(2));
    await expect(Reflect.apply(updateUserRoles, undefined, [id(2), body])).rejects.toMatchObject({
      status: 400,
    });
    expect(await getUserDetail(id(2))).toEqual(before);
  });

  it('AC-007.2 allows a manager to retain their own Space and rejects another occupied Space with MSG117', async () => {
    const before = await getUserDetail(id(3));
    await expect(
      updateUserRoles(id(2), {
        roles: ['KNOWLEDGE_MANAGER'],
        departmentId: null,
        spaceIds: [IT_SPACE],
      }),
    ).rejects.toMatchObject({ code: 'MSG117', params: { space_name: 'IT Knowledge' } });
    const after = await updateUserRoles(id(3), {
      roles: ['KNOWLEDGE_MANAGER'],
      departmentId: before.department?.departmentId ?? null,
      spaceIds: [IT_SPACE],
    });
    expect(after.managedSpaces).toEqual(before.managedSpaces);
    expect(store.spaceHistory).toEqual([]);
  });

  it('AC-007.3 requires handover of Active Projects in the old Department before moving a Project Manager', async () => {
    const hrManager = await createUser({
      email: 'hr.pm@company.example',
      roles: ['PROJECT_MANAGER'],
      departmentId: HR,
    });
    const options = await getUserHandoverOptions(id(5));
    const body: UpdateUserRolesRequest = {
      roles: ['PROJECT_MANAGER'],
      departmentId: HR,
      spaceIds: [],
      projectHandovers: options.projects.map((item) => ({
        projectId: item.project.projectId,
        recipientId: hrManager.id,
      })),
    };
    const before = await getUserDetail(id(5));
    await expect(updateUserRoles(id(5), body)).rejects.toMatchObject({ status: 400 });
    expect(await getUserDetail(id(5))).toEqual(before);
    const after = await updateUserRoles(id(5), {
      ...body,
      projectHandovers: options.projects.map((item) => ({
        projectId: item.project.projectId,
        recipientId: id(9),
      })),
    });
    expect(after.department?.departmentId).toBe(HR);
    expect(
      after.projects
        .filter((project) => project.isActive)
        .every((project) => project.managerId === id(9)),
    ).toBe(true);
  });

  it('AC-007.3 hands over only removed Spaces while retaining and adding selected Spaces', async () => {
    const after = await updateUserRoles(id(7), {
      roles: ['KNOWLEDGE_MANAGER'],
      departmentId: null,
      spaceIds: [HANDBOOK],
      spaceHandovers: [{ spaceId: HR_SPACE, recipientId: id(3) }],
    });
    expect(after.managedSpaces.map((space) => space.spaceId)).toEqual([HANDBOOK]);
    expect((await getUserDetail(id(3))).managedSpaces.map((space) => space.spaceId)).toEqual([
      IT_SPACE,
      HR_SPACE,
    ]);
    expect(store.spaceHistory).toContainEqual({
      spaceId: HR_SPACE,
      previousUserId: id(7),
      recipientId: id(3),
    });
  });

  it('AC-007.3 rejects missing, duplicate and extra responsibility choices without role or ownership changes', async () => {
    const before = await getUserDetail(id(5));
    const options = await getUserHandoverOptions(id(5));
    const first = options.projects[0];
    expect(first).toBeDefined();
    if (!first) return;
    for (const projectHandovers of [
      [],
      [
        { projectId: first.project.projectId, recipientId: id(9) },
        { projectId: first.project.projectId, recipientId: id(9) },
      ],
      [
        ...options.projects.map((item) => ({
          projectId: item.project.projectId,
          recipientId: id(9),
        })),
        { projectId: 'unknown', recipientId: id(9) },
      ],
    ]) {
      await expect(updateUserRoles(id(5), { ...EMPLOYEE, projectHandovers })).rejects.toMatchObject(
        { status: 400 },
      );
      expect(await getUserDetail(id(5))).toEqual(before);
    }
  });

  it('AC-007.4 rejects removing own Admin with MSG113 and the last Active Admin with MSG56', async () => {
    await expect(updateUserRoles(id(1), EMPLOYEE)).rejects.toMatchObject({ code: 'MSG113' });
    await updateUserStatus(id(8), { status: 'DEACTIVATED' });
    await expect(updateUserRoles(id(1), EMPLOYEE)).rejects.toMatchObject({ code: 'MSG56' });
    expect((await getUserDetail(id(1))).roles).toContain('ADMIN');
  });

  it('AC-007.5 requires MSG78 confirmation and removes memberships while retaining Project history', async () => {
    const before = await getUserDetail(id(4));
    await expect(updateUserRoles(id(4), EMPLOYEE)).rejects.toMatchObject({
      code: 'MSG78',
      params: { user_name: 'Phạm Quốc Bảo', count: 4 },
    });
    expect(await getUserDetail(id(4))).toEqual(before);
    const after = await updateUserRoles(id(4), { ...EMPLOYEE, confirmTicketResolverRemoval: true });
    expect(after.ticketResolverProjectIds).toEqual([]);
    expect(after.projects.map((project) => project.projectId)).toEqual(
      before.projects.map((project) => project.projectId),
    );
    setDevUser('tr');
    const session = await getMe();
    expect(session.roles).toEqual(['EMPLOYEE']);
    expect(session.projects).toEqual([]);
  });

  it('AC-007.5 excludes retained PM ownership from the removal confirmation and preserves management', async () => {
    await updateUserRoles(id(5), {
      roles: ['PROJECT_MANAGER', 'TICKET_RESOLVER'],
      departmentId: IT,
      spaceIds: [],
    });
    expect((await getUserDetail(id(5))).ticketResolverProjectIds).toEqual([]);
    const after = await updateUserRoles(id(5), {
      roles: ['PROJECT_MANAGER'],
      departmentId: IT,
      spaceIds: [],
    });
    expect(
      after.projects
        .filter((project) => project.isActive)
        .every((project) => project.managerId === id(5)),
    ).toBe(true);
  });

  it('AC-007.6 updates /me and next-request API authorization after role grants and removals', async () => {
    await updateUserRoles(id(2), { roles: ['ADMIN'], departmentId: null, spaceIds: [] });
    setDevUser('employee');
    expect((await getMe()).roles).toEqual(['EMPLOYEE', 'ADMIN']);
    expect((await getUsers()).page.totalElements).toBe(24);
    setDevUser('admin');
    await updateUserRoles(id(2), EMPLOYEE);
    setDevUser('employee');
    expect((await getMe()).roles).toEqual(['EMPLOYEE']);
    await expect(getUsers()).rejects.toMatchObject({ status: 403 });
  });

  it('AC-007.6 drops former Project permissions while retaining handover history after PM removal (BUG-027)', async () => {
    const before = await getUserDetail(id(5));
    const options = await getUserHandoverOptions(id(5));
    await updateUserRoles(id(5), {
      ...EMPLOYEE,
      projectHandovers: options.projects.map((item) => ({
        projectId: item.project.projectId,
        recipientId: id(9),
      })),
    });
    expect((await getUserDetail(id(5))).projects.map((project) => project.projectId)).toEqual(
      before.projects.map((project) => project.projectId),
    );
    setDevUser('tr-two-departments');
    const session = await getMe();
    expect(session.roles).toEqual(['EMPLOYEE']);
    expect(session.projects).toEqual([]);
  });

  it('AC-007.6 keeps explicit assignments authoritative when changed back to initial list values', async () => {
    await updateUserRoles(id(7), {
      roles: ['KNOWLEDGE_MANAGER', 'ADMIN'],
      departmentId: null,
      spaceIds: [HR_SPACE],
    });
    const original = await getUserDetail(id(7));
    await updateUserRoles(id(7), {
      roles: ['KNOWLEDGE_MANAGER'],
      departmentId: IT,
      spaceIds: [HR_SPACE],
    });
    setDevUser('pm-km');
    const session = await getMe();
    expect(session.roles).toEqual(['EMPLOYEE', 'KNOWLEDGE_MANAGER']);
    expect(session.department).toBeNull();
    expect(session.departments).toEqual([]);
    expect(session.managedSpaces.map((space) => space.spaceId)).toEqual(
      original.managedSpaces.map((space) => space.spaceId),
    );
  });

  it('AC-007.6 projects session Departments from current roles and retained memberships (BUG-032)', async () => {
    await updateUserRoles(id(4), { roles: ['TICKET_RESOLVER'], departmentId: HR, spaceIds: [] });
    setDevUser('tr');
    const resolver = await getMe();
    expect(resolver.department).toBeNull();
    expect(resolver.departments).toEqual([{ departmentId: IT, name: 'IT' }]);
    setDevUser('admin');
    await updateUserRoles(id(2), { roles: ['PROJECT_MANAGER'], departmentId: HR, spaceIds: [] });
    setDevUser('employee');
    const manager = await getMe();
    expect(manager.department).toEqual({ departmentId: HR, name: 'HR' });
    expect(manager.departments).toEqual([{ departmentId: HR, name: 'HR' }]);
    setDevUser('admin');
    await updateUserRoles(id(4), {
      roles: [],
      departmentId: HR,
      spaceIds: [],
      confirmTicketResolverRemoval: true,
    });
    setDevUser('tr');
    const employee = await getMe();
    expect(employee.department).toBeNull();
    expect(employee.departments).toEqual([]);
  });

  it('AC-007.7 validates combined Project/Space handovers before committing any role, assignment or history writes', async () => {
    await updateUserRoles(id(5), {
      roles: ['PROJECT_MANAGER', 'KNOWLEDGE_MANAGER'],
      departmentId: IT,
      spaceIds: [HANDBOOK],
    });
    const before = await getUserDetail(id(5));
    const options = await getUserHandoverOptions(id(5));
    const body: UpdateUserRolesRequest = {
      ...EMPLOYEE,
      projectHandovers: options.projects.map((item) => ({
        projectId: item.project.projectId,
        recipientId: id(9),
      })),
      spaceHandovers: [{ spaceId: HANDBOOK, recipientId: id(2) }],
    };
    await expect(updateUserRoles(id(5), body)).rejects.toMatchObject({ status: 400 });
    expect(await getUserDetail(id(5))).toEqual(before);
    expect(store.spaceHistory).toEqual([]);
    await updateUserRoles(id(5), {
      ...body,
      spaceHandovers: [{ spaceId: HANDBOOK, recipientId: id(3) }],
    });
    expect((await getUserDetail(id(5))).roles).toEqual(['EMPLOYEE']);
    expect(store.spaceHistory).toContainEqual({
      spaceId: HANDBOOK,
      previousUserId: id(5),
      recipientId: id(3),
    });
  });

  it('AC-007.7 rechecks replacement eligibility rather than trusting earlier option responses', async () => {
    const options = await getUserHandoverOptions(id(5));
    const other = await getUserHandoverOptions(id(9));
    await updateUserStatus(id(9), {
      status: 'DEACTIVATED',
      projectHandovers: other.projects.map((item) => ({
        projectId: item.project.projectId,
        recipientId: id(13),
      })),
    });
    const before = await getUserDetail(id(5));
    await expect(
      updateUserRoles(id(5), {
        ...EMPLOYEE,
        projectHandovers: options.projects.map((item) => ({
          projectId: item.project.projectId,
          recipientId: id(9),
        })),
      }),
    ).rejects.toMatchObject({ status: 400 });
    expect(await getUserDetail(id(5))).toEqual(before);
  });

  it('AC-007.7 rejects foreign targets and non-Admin or signed-out actors', async () => {
    await expect(
      updateUserRoles('20000000-0000-4000-8000-000000000099', EMPLOYEE),
    ).rejects.toMatchObject({ status: 404 });
    setDevUser('employee');
    await expect(updateUserRoles(id(4), EMPLOYEE)).rejects.toMatchObject({ status: 403 });
    setDevUser(null);
    await expect(updateUserRoles(id(4), EMPLOYEE)).rejects.toMatchObject({ status: 401 });
  });
});
