import { getMyViewableSpaces, isApiError } from '@/shared/api';
import { setDevUser, type DevUserId } from '@/shared/auth';

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
