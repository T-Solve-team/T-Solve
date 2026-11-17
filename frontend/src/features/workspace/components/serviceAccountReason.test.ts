import { serviceAccountReason } from './serviceAccountReason';

describe('serviceAccountReason', () => {
  it('AC-009.8 explains a rejected token', () => {
    expect(serviceAccountReason({ tracker_name: 'Jira', reason: 'TOKEN_REJECTED' })).toBe(
      'Jira did not accept this token. Check that it is a scoped API token of the service account with the required scopes.',
    );
  });

  it('AC-009.8 names the missing permissions in their order', () => {
    expect(
      serviceAccountReason({
        reason: 'MISSING_PERMISSIONS',
        missing_permissions: 'BROWSE_PROJECTS,CREATE_ISSUES,MODIFY_REPORTER',
      }),
    ).toBe('Missing Jira permissions: Browse projects, Create issues, Modify reporter.');
    expect(
      serviceAccountReason({
        reason: 'MISSING_PERMISSIONS',
        missing_permissions: 'MODIFY_REPORTER',
      }),
    ).toBe('Missing Jira permissions: Modify reporter.');
  });

  it('AC-009.8 explains an unreachable Jira', () => {
    expect(serviceAccountReason({ reason: 'JIRA_UNREACHABLE' })).toBe(
      'Jira could not be reached. Try again later.',
    );
  });

  it.each([
    [{}],
    [{ reason: 'SOMETHING_ELSE' }],
    [{ reason: 'MISSING_PERMISSIONS' }],
    [{ reason: 'MISSING_PERMISSIONS', missing_permissions: 'UNKNOWN_KEY' }],
  ])('AC-009.8 adds no line for an unknown or absent reason: %o', (params) => {
    expect(serviceAccountReason(params)).toBeUndefined();
  });
});
