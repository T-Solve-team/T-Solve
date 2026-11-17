import {
  getUserDetail,
  getUserHandoverOptions,
  updateUserRoles,
  updateUserStatus,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { HANDBOOK_SPACE_ID, IT_SPACE_ID } from './data';
import { store } from './state';
const id = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
function fixture(n: number) {
  const user = store.users.find((item) => item.id === id(n));
  if (!user) throw new Error('Missing fixture');
  return user;
}
describe('Several Knowledge Managers', () => {
  beforeEach(() => setDevUser('admin'));
  it('AC-007.9 excludes co-managed Spaces but retains the last Active manager Space', async () => {
    fixture(3).spaceIds.push(HANDBOOK_SPACE_ID);
    fixture(7).spaceIds.push(IT_SPACE_ID);
    expect((await getUserHandoverOptions(id(3))).spaces.map(({ space }) => space.spaceId)).toEqual([
      HANDBOOK_SPACE_ID,
    ]);
  });
  it.each(['DEACTIVATED', 'without role'] as const)(
    'AC-006.14 ignores a co-manager %s and still requires handover',
    async (condition) => {
      fixture(7).spaceIds.push(IT_SPACE_ID);
      if (condition === 'DEACTIVATED') fixture(7).status = 'DEACTIVATED';
      else fixture(7).roles = ['EMPLOYEE'];
      expect(
        (await getUserHandoverOptions(id(3))).spaces.map(({ space }) => space.spaceId),
      ).toEqual([IT_SPACE_ID]);
      await expect(updateUserStatus(id(3), { status: 'DEACTIVATED' })).rejects.toMatchObject({
        status: 400,
        code: 'MSG01',
      });
      expect(fixture(3).spaceIds).toEqual([IT_SPACE_ID]);
    },
  );
  it.each(['status', 'roles'] as const)(
    'AC-006.15 / AC-007.16 / AC-007.19 removes co-managed assignments without handover via %s',
    async (action) => {
      fixture(7).spaceIds.push(IT_SPACE_ID);
      if (action === 'status') await updateUserStatus(id(3), { status: 'DEACTIVATED' });
      else await updateUserRoles(id(3), { roles: [], departmentId: null, spaceIds: [] });
      expect((await getUserDetail(id(3))).managedSpaces).toEqual([]);
      expect(fixture(7).spaceIds).toContain(IT_SPACE_ID);
      expect(store.spaceHistory).toEqual([]);
      expect(
        (await getUserHandoverOptions(id(7))).spaces.map(({ space }) => space.spaceId),
      ).toContain(IT_SPACE_ID);
    },
  );
});
