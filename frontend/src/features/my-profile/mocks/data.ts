import type { SpaceRef } from '@/shared/api';
import type { DevUserId } from '@/shared/auth';

const space = (n: number, name: string): SpaceRef => ({
  spaceId: `00000000-0000-4000-8000-${n.toString(16).padStart(12, '0')}`,
  name,
});

// Same ids as the managed Spaces of the fixture users (src/mocks/session.ts).
const IT_SPACE = space(0xa1, 'IT Space');
const HR_SPACE = space(0xa2, 'HR Space');
const LONG_NAME = `${'Regional support knowledge '.repeat(5)}`.slice(0, 119) + '…';

/** The Spaces each dev user can view (AC-003.3); the handler sorts them by name. */
export const VIEWABLE_SPACES: Record<DevUserId, SpaceRef[]> = {
  employee: [],
  tr: [IT_SPACE, space(0xb1, 'Onboarding'), space(0xb2, 'Network')],
  'tr-two-departments': [IT_SPACE, HR_SPACE, space(0xb1, 'Onboarding'), space(0xb3, 'Policies')],
  pm: [IT_SPACE, space(0xb4, 'Projects')],
  km: [IT_SPACE, space(0xb5, 'Runbooks'), space(0xb6, 'Archive')],
  admin: [space(0xb7, 'Company wiki')],
  'pm-km': [HR_SPACE, space(0xb4, 'Projects')],
  'admin-tr': [
    IT_SPACE,
    HR_SPACE,
    space(0xb8, 'Finance'),
    space(0xb9, 'Legal'),
    space(0xba, 'Operations'),
    space(0xbb, LONG_NAME),
  ],
};
