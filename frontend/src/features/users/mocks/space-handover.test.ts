import {
  getUserDetail,
  getUserHandoverOptions,
  updateUserRoles,
  updateUserStatus,
  type SpaceHandover,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { HANDBOOK_SPACE_ID, HR_SPACE_ID, IT_SPACE_ID } from './data';
import { store } from './state';

const id = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const targetId = id(3);
const recipientId = id(7);
const choices = [IT_SPACE_ID, HANDBOOK_SPACE_ID].map((spaceId) => ({ spaceId, recipientId }));

function fixtureUser(userId: string) {
  const user = store.users.find((item) => item.id === userId);
  if (!user) throw new Error(`Missing fixture ${userId}`);
  return user;
}

function snapshot() {
  return structuredClone({
    users: store.users,
    projects: store.projects,
    history: store.spaceHistory,
  });
}

describe('Space handover contract for UC-06 and UC-07', () => {
  beforeEach(() => {
    setDevUser('admin');
    fixtureUser(targetId).spaceIds.push(HANDBOOK_SPACE_ID);
    fixtureUser('20000000-0000-4000-8000-000000000099').roles = ['EMPLOYEE', 'KNOWLEDGE_MANAGER'];
  });

  it('AC-009.3 offers other Active Workspace Knowledge Managers across Departments', async () => {
    fixtureUser(recipientId).department = {
      departmentId: '00000000-0000-4000-8000-0000000000d2',
      name: 'HR',
    };
    const inactive = fixtureUser(id(11));
    inactive.status = 'DEACTIVATED';
    const foreign = fixtureUser('20000000-0000-4000-8000-000000000099');
    const options = await getUserHandoverOptions(targetId);
    expect(options.spaces.map(({ space }) => space.spaceId)).toEqual([
      IT_SPACE_ID,
      HANDBOOK_SPACE_ID,
    ]);
    for (const { recipients } of options.spaces) {
      expect(recipients.map((candidate) => candidate.id)).toContain(recipientId);
      expect(recipients.map((candidate) => candidate.id)).not.toEqual(
        expect.arrayContaining([targetId]),
      );
      expect(recipients.map((candidate) => candidate.id)).not.toContain(inactive.id);
      expect(recipients.map((candidate) => candidate.id)).not.toContain(foreign.id);
      expect(recipients.map((candidate) => candidate.id)).not.toContain(id(2));
      for (const candidate of recipients) {
        expect(candidate.status).toBe('ACTIVE');
        expect(candidate.roles).toContain('KNOWLEDGE_MANAGER');
      }
    }
  });

  describe.each(['status', 'roles'] as const)('%s caller', (caller) => {
    function submit(spaceHandovers: SpaceHandover[]) {
      return caller === 'status'
        ? updateUserStatus(targetId, { status: 'DEACTIVATED', spaceHandovers })
        : updateUserRoles(targetId, {
            roles: [],
            departmentId: null,
            spaceIds: [],
            spaceHandovers,
          });
    }

    it.each([
      ['missing', choices.slice(0, 1)],
      ['duplicate', [choices[0], choices[0]]],
      ['unrelated', [choices[0], { spaceId: HR_SPACE_ID, recipientId }]],
      ['self', choices.map((choice) => ({ ...choice, recipientId: targetId }))],
      ['wrong role', choices.map((choice) => ({ ...choice, recipientId: id(2) }))],
      [
        'foreign Workspace',
        choices.map((choice) => ({
          ...choice,
          recipientId: '20000000-0000-4000-8000-000000000099',
        })),
      ],
      ['unknown recipient', choices.map((choice) => ({ ...choice, recipientId: id(999) }))],
    ])('AC-009.4 rejects %s choices without any partial changes', async (_name, handovers) => {
      const before = snapshot();
      await expect(
        submit(handovers.filter((choice) => choice !== undefined)),
      ).rejects.toMatchObject({ status: 400, code: 'MSG01' });
      expect(snapshot()).toEqual(before);
    });

    it.each(['deactivation', 'role removal'] as const)(
      'AC-009.4 revalidates a recipient after %s between lookup and submission',
      async (change) => {
        const options = await getUserHandoverOptions(targetId);
        expect(
          options.spaces.every((item) =>
            item.recipients.some((candidate) => candidate.id === recipientId),
          ),
        ).toBe(true);
        const recipient = fixtureUser(recipientId);
        if (change === 'deactivation') recipient.status = 'DEACTIVATED';
        else recipient.roles = ['EMPLOYEE'];
        const before = snapshot();
        // The first Space has a valid different recipient; the second has gone stale.
        await expect(
          submit([
            { ...choices[0], spaceId: IT_SPACE_ID, recipientId: id(15) },
            { spaceId: HANDBOOK_SPACE_ID, recipientId },
          ]),
        ).rejects.toMatchObject({ status: 400 });
        expect(snapshot()).toEqual(before);
      },
    );

    it('AC-009.5 returns MSG118 when no eligible Knowledge Manager remains', async () => {
      for (const user of store.users) {
        if (user.id !== targetId && user.roles.includes('KNOWLEDGE_MANAGER'))
          user.status = 'DEACTIVATED';
      }
      const before = snapshot();
      await expect(submit(choices)).rejects.toMatchObject({ status: 409, code: 'MSG118' });
      expect(snapshot()).toEqual(before);
    });

    it('AC-009.7 transfers multiple Spaces once to the same recipient and retains history', async () => {
      await submit(choices);
      expect((await getUserDetail(targetId)).managedSpaces).toEqual([]);
      expect(
        (await getUserDetail(recipientId)).managedSpaces.map((space) => space.spaceId),
      ).toEqual([IT_SPACE_ID, HR_SPACE_ID, HANDBOOK_SPACE_ID]);
      expect(store.spaceHistory).toEqual(
        choices.map((choice) => ({ ...choice, previousUserId: targetId })),
      );
      const after = snapshot();
      await expect(submit(choices)).rejects.toMatchObject({ status: 400 });
      expect(snapshot()).toEqual(after);
    });

    it('AC-009.9 keeps completed handovers after reactivation or restoring Knowledge Manager', async () => {
      await submit(choices);
      if (caller === 'status') await updateUserStatus(targetId, { status: 'ACTIVE' });
      else {
        // Release another Space to make a valid new KM assignment available.
        fixtureUser(recipientId).spaceIds = fixtureUser(recipientId).spaceIds.filter(
          (spaceId) => spaceId !== HR_SPACE_ID,
        );
        await updateUserRoles(targetId, {
          roles: ['KNOWLEDGE_MANAGER'],
          departmentId: null,
          spaceIds: [HR_SPACE_ID],
        });
      }
      const retained = (await getUserDetail(recipientId)).managedSpaces.map(
        (space) => space.spaceId,
      );
      expect(retained).toEqual(expect.arrayContaining([IT_SPACE_ID, HANDBOOK_SPACE_ID]));
      expect(
        (await getUserDetail(targetId)).managedSpaces.map((space) => space.spaceId),
      ).not.toContain(IT_SPACE_ID);
      expect(store.spaceHistory).toHaveLength(2);
    });
  });

  it('AC-009.2 hands over only removed Spaces and retains selected ownership', async () => {
    const after = await updateUserRoles(targetId, {
      roles: ['KNOWLEDGE_MANAGER'],
      departmentId: null,
      spaceIds: [HANDBOOK_SPACE_ID],
      spaceHandovers: [{ spaceId: IT_SPACE_ID, recipientId }],
    });
    expect(after.managedSpaces.map((space) => space.spaceId)).toEqual([HANDBOOK_SPACE_ID]);
    expect((await getUserDetail(recipientId)).managedSpaces.map((space) => space.spaceId)).toEqual([
      IT_SPACE_ID,
      HR_SPACE_ID,
    ]);
  });

  it.each(['status', 'roles'] as const)(
    'AC-009.7 rejects invalid Space choices without transferring accompanying Projects via %s',
    async (caller) => {
      const target = fixtureUser(id(5));
      target.roles.push('KNOWLEDGE_MANAGER');
      target.spaceIds = fixtureUser(targetId).spaceIds;
      fixtureUser(targetId).spaceIds = [];
      const options = await getUserHandoverOptions(target.id);
      const projectHandovers = options.projects.map(({ project }) => ({
        projectId: project.projectId,
        recipientId: id(9),
      }));
      const before = snapshot();
      const spaceHandovers = [
        { spaceId: IT_SPACE_ID, recipientId },
        { spaceId: HANDBOOK_SPACE_ID, recipientId: id(2) },
      ];
      const request =
        caller === 'status'
          ? updateUserStatus(target.id, { status: 'DEACTIVATED', projectHandovers, spaceHandovers })
          : updateUserRoles(target.id, {
              roles: [],
              departmentId: null,
              spaceIds: [],
              projectHandovers,
              spaceHandovers,
            });
      await expect(request).rejects.toMatchObject({ status: 400 });
      expect(snapshot()).toEqual(before);
    },
  );
});
