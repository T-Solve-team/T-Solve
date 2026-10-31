import {
  createUser,
  getMe,
  getUserDetail,
  getUserHandoverOptions,
  getUsers,
  updateUserStatus,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { store } from './state';

const id = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;

describe('Workspace account status contract', () => {
  beforeEach(() => setDevUser('admin'));

  it('AC-006.7 retains former-user Space and recipient references only after a successful handover (BUG-030)', async () => {
    const spaceId = '00000000-0000-4000-8000-0000000000a1';
    await expect(
      updateUserStatus(id(3), {
        status: 'DEACTIVATED',
        spaceHandovers: [{ spaceId, recipientId: id(2) }],
      }),
    ).rejects.toMatchObject({ status: 400 });
    expect(store.spaceHistory).toEqual([]);
    expect((await getUserDetail(id(3))).managedSpaces.map((space) => space.spaceId)).toContain(
      spaceId,
    );
    await updateUserStatus(id(3), {
      status: 'DEACTIVATED',
      spaceHandovers: [{ spaceId, recipientId: id(7) }],
    });
    expect(store.spaceHistory).toEqual([{ spaceId, previousUserId: id(3), recipientId: id(7) }]);
    expect((await getUserDetail(id(3))).managedSpaces).toEqual([]);
    expect((await getUserDetail(id(7))).managedSpaces.map((space) => space.spaceId)).toContain(
      spaceId,
    );
    expect((await getUserDetail(id(3))).id).toBe(id(3));
  });

  it('AC-006.5 / AC-008.7 retains the Project tracker key when assigning a new manager (BUG-031)', async () => {
    // The dev pm's central fixture is a PM; align this test's list fixture before assigning it Projects.
    const recipient = store.users.find((user) => user.id === id(3));
    expect(recipient).toBeDefined();
    if (!recipient) return;
    recipient.roles = ['EMPLOYEE', 'PROJECT_MANAGER'];
    const options = await getUserHandoverOptions(id(5));
    await updateUserStatus(id(5), {
      status: 'DEACTIVATED',
      projectHandovers: options.projects.map((item) => ({
        projectId: item.project.projectId,
        recipientId: id(3),
      })),
    });
    setDevUser('pm');
    const project = (await getMe()).projects.find(
      (item) => item.projectId === '00000000-0000-4000-8000-000000000101',
    );
    expect(project).toMatchObject({ jiraProjectKey: 'ITSUP', roleInProject: 'PROJECT_MANAGER' });
  });

  it('AC-006.1 reactivates an account retaining its UUID and list data', async () => {
    const before = await getUserDetail(id(11));
    expect(before.status).toBe('DEACTIVATED');
    const after = await updateUserStatus(id(11), { status: 'ACTIVE' });
    expect(after).toEqual({ ...before, status: 'ACTIVE' });
    expect((await getUsers({ q: before.email })).content[0]?.status).toBe('ACTIVE');
  });

  it('AC-006.3 protects the last Active Admin before the self-deactivation check', async () => {
    await updateUserStatus(id(8), { status: 'DEACTIVATED' });
    await expect(updateUserStatus(id(1), { status: 'DEACTIVATED' })).rejects.toMatchObject({
      status: 409,
      code: 'MSG56',
    });
    expect((await getUserDetail(id(1))).status).toBe('ACTIVE');
  });

  it('AC-006.4 rejects ordinary self-deactivation and keeps untouched central sessions intact', async () => {
    await expect(updateUserStatus(id(1), { status: 'DEACTIVATED' })).rejects.toMatchObject({
      code: 'MSG112',
    });
    setDevUser('pm');
    expect((await getMe()).roles).toEqual(['EMPLOYEE', 'PROJECT_MANAGER']);
    setDevUser('km');
    expect((await getMe()).managedSpaces).toEqual([
      { spaceId: '00000000-0000-4000-8000-0000000000a1', name: 'IT Space' },
    ]);
  });

  it('AC-006.5 / AC-008.3 transfers every Active Project atomically to an eligible same-Department manager', async () => {
    const options = await getUserHandoverOptions(id(5));
    expect(options.projects).toHaveLength(2);
    for (const item of options.projects) {
      expect(item.recipients.length).toBeGreaterThan(0);
      for (const recipient of item.recipients) {
        expect(recipient.id).not.toBe(id(5));
        expect(recipient.status).toBe('ACTIVE');
        expect(recipient.roles).toContain('PROJECT_MANAGER');
        expect(recipient.department?.departmentId).toBe(item.project.department.departmentId);
      }
    }
    await updateUserStatus(id(5), {
      status: 'DEACTIVATED',
      projectHandovers: options.projects.map((item) => ({
        projectId: item.project.projectId,
        recipientId: id(9),
      })),
    });
    expect((await getUserHandoverOptions(id(5))).projects).toEqual([]);
    const after = await getUserDetail(id(9));
    expect(
      after.projects.filter((project) => project.managerId === id(9) && project.isActive),
    ).toHaveLength(3);
  });

  it('AC-006.5 transfers Space ownership and updates only changed mock sessions', async () => {
    await updateUserStatus(id(3), {
      status: 'DEACTIVATED',
      spaceHandovers: [{ spaceId: '00000000-0000-4000-8000-0000000000a1', recipientId: id(7) }],
    });
    expect((await getUserDetail(id(7))).managedSpaces.map((space) => space.spaceId)).toEqual([
      '00000000-0000-4000-8000-0000000000a1',
      '00000000-0000-4000-8000-0000000000a2',
    ]);
    setDevUser('pm');
    await expect(getMe()).rejects.toMatchObject({ status: 401 });
    await expect(getUsers()).rejects.toMatchObject({ status: 401 });
    setDevUser('pm-km');
    expect((await getMe()).managedSpaces.map((space) => space.spaceId)).toEqual([
      '00000000-0000-4000-8000-0000000000a1',
      '00000000-0000-4000-8000-0000000000a2',
    ]);
  });

  it('AC-006.5 preserves a recipient session role and Department during a status-only handover (BUG-029)', async () => {
    setDevUser('pm-km');
    const before = await getMe();
    setDevUser('admin');
    await updateUserStatus(id(3), {
      status: 'DEACTIVATED',
      spaceHandovers: [{ spaceId: '00000000-0000-4000-8000-0000000000a1', recipientId: id(7) }],
    });
    setDevUser('pm-km');
    const after = await getMe();
    expect(after.roles).toEqual(before.roles);
    expect(after.department).toEqual(before.department);
    expect(after.user).toEqual(before.user);
  });

  it('AC-006.6 returns MSG59 when no eligible same-Department Project Manager remains', async () => {
    const target = await createUser({
      email: 'only.hr@company.example',
      roles: ['PROJECT_MANAGER'],
      departmentId: '00000000-0000-4000-8000-0000000000d2',
    });
    expect(target.department?.departmentId).toBe('00000000-0000-4000-8000-0000000000d2');
    // Seed Project IT requires IT managers; deactivate every eligible replacement first.
    for (const n of [9, 13, 17, 21]) {
      const options = await getUserHandoverOptions(id(n));
      await updateUserStatus(id(n), {
        status: 'DEACTIVATED',
        projectHandovers: options.projects.map((item) => ({
          projectId: item.project.projectId,
          recipientId: id(5),
        })),
      });
    }
    await expect(updateUserStatus(id(5), { status: 'DEACTIVATED' })).rejects.toMatchObject({
      code: 'MSG59',
      params: { department_name: 'IT' },
    });
    expect(
      (await getUserHandoverOptions(id(5))).projects.every((item) => item.recipients.length === 0),
    ).toBe(true);
  });

  it('AC-006.6 returns MSG118 when no active Knowledge Manager can receive a Space', async () => {
    for (const n of [7, 11, 15, 19, 23]) {
      const options = await getUserHandoverOptions(id(n));
      await updateUserStatus(id(n), {
        status: 'DEACTIVATED',
        spaceHandovers: options.spaces.map((item) => ({
          spaceId: item.space.spaceId,
          recipientId: id(3),
        })),
      });
    }
    await expect(updateUserStatus(id(3), { status: 'DEACTIVATED' })).rejects.toMatchObject({
      code: 'MSG118',
    });
  });

  it('AC-006.7 / AC-008.4 retains account, memberships and inactive Project history after handover', async () => {
    const before = await getUserDetail(id(5));
    const options = await getUserHandoverOptions(id(5));
    await updateUserStatus(id(5), {
      status: 'DEACTIVATED',
      projectHandovers: options.projects.map((item) => ({
        projectId: item.project.projectId,
        recipientId: id(9),
      })),
    });
    const after = await getUserDetail(id(5));
    expect(after.id).toBe(before.id);
    expect(after.email).toBe(before.email);
    expect(after.projects.map((project) => project.projectId)).toEqual(
      before.projects.map((project) => project.projectId),
    );
    expect(after.projects.filter((project) => !project.isActive)).toEqual(
      before.projects.filter((project) => !project.isActive),
    );
    expect((await getUsers()).page.totalElements).toBe(24);
  });

  it('AC-008.9 reactivation keeps all Project handovers and unrelated Projects unchanged', async () => {
    const before = structuredClone(store.projects);
    const options = await getUserHandoverOptions(id(5));
    const choices = options.projects.map((item, index) => ({
      projectId: item.project.projectId,
      recipientId: id(index === 0 ? 9 : 13),
    }));
    expect(choices).toHaveLength(2);
    await updateUserStatus(id(5), { status: 'DEACTIVATED', projectHandovers: choices });
    await updateUserStatus(id(5), { status: 'ACTIVE' });
    expect((await getUserDetail(id(5))).status).toBe('ACTIVE');
    expect((await getUserHandoverOptions(id(5))).projects).toEqual([]);
    for (const project of store.projects) {
      const choice = choices.find((item) => item.projectId === project.projectId);
      if (choice) {
        expect(project.managerId).toBe(choice.recipientId);
        expect(project.memberIds).toContain(choice.recipientId);
        expect(project.jiraProjectKey).toBe(
          before.find((item) => item.projectId === project.projectId)?.jiraProjectKey,
        );
      } else {
        expect(project).toEqual(before.find((item) => item.projectId === project.projectId));
      }
    }
  });

  it('AC-006.8 rejects an invalid second recipient without partial ownership or status changes', async () => {
    const before = await getUserDetail(id(5));
    const options = await getUserHandoverOptions(id(5));
    const first = options.projects[0];
    const second = options.projects[1];
    expect(first).toBeDefined();
    expect(second).toBeDefined();
    if (!first || !second) return;
    for (const recipientId of [
      id(2),
      id(5),
      id(11),
      '20000000-0000-4000-8000-000000000099',
      'unknown',
    ]) {
      await expect(
        updateUserStatus(id(5), {
          status: 'DEACTIVATED',
          projectHandovers: [
            { projectId: first.project.projectId, recipientId: id(9) },
            { projectId: second.project.projectId, recipientId },
          ],
        }),
      ).rejects.toMatchObject({ status: 400 });
      expect(await getUserDetail(id(5))).toEqual(before);
    }
  });

  it('AC-006.8 rejects unknown status/body, missing/duplicate/extra handovers and foreign targets', async () => {
    const before = await getUserDetail(id(5));
    const options = await getUserHandoverOptions(id(5));
    const project = options.projects[0]?.project;
    expect(project).toBeDefined();
    if (!project) return;
    for (const body of [
      null,
      {},
      { status: 'REMOVED' },
      { status: 'DEACTIVATED' },
      { status: 'DEACTIVATED', projectHandovers: 'bad' },
      {
        status: 'DEACTIVATED',
        projectHandovers: [
          { projectId: project.projectId, recipientId: id(9) },
          { projectId: project.projectId, recipientId: id(9) },
        ],
      },
      {
        status: 'DEACTIVATED',
        spaceHandovers: [{ spaceId: '00000000-0000-4000-8000-000000000099', recipientId: id(7) }],
      },
    ]) {
      await expect(Reflect.apply(updateUserStatus, undefined, [id(5), body])).rejects.toMatchObject(
        { status: 400 },
      );
      expect(await getUserDetail(id(5))).toEqual(before);
    }
    await expect(getUserDetail('20000000-0000-4000-8000-000000000099')).rejects.toMatchObject({
      status: 404,
    });
    await expect(
      updateUserStatus('20000000-0000-4000-8000-000000000099', { status: 'DEACTIVATED' }),
    ).rejects.toMatchObject({ status: 404 });
  });

  it('AC-006.8 rechecks actor permissions and session status on every request', async () => {
    await updateUserStatus(id(8), { status: 'DEACTIVATED' });
    setDevUser('admin-tr');
    await expect(getMe()).rejects.toMatchObject({ status: 401 });
    await expect(getUserDetail(id(2))).rejects.toMatchObject({ status: 401 });
    await expect(updateUserStatus(id(2), { status: 'DEACTIVATED' })).rejects.toMatchObject({
      status: 401,
    });
    setDevUser('employee');
    await expect(getUserHandoverOptions(id(2))).rejects.toMatchObject({ status: 403 });
  });
});
