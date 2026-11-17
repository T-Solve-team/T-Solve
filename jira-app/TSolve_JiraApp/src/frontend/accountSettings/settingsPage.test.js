import { settingsPagePath } from './settingsPage';

const APP = '0e9fd967-781d-473e-9b2c-9388d71eafbf';
const ENV = 'a1b2c3d4-e5f6-4a1b-8c9d-0e1f2a3b4c5d';

describe('settingsPagePath', () => {
  it('AC-008.1 builds the personal settings page path of this app from the Forge context', () => {
    const context = { localId: `ari:cloud:ecosystem::extension/${APP}/${ENV}/static/tsolve-issue-panel`, environmentId: ENV };

    expect(settingsPagePath(context)).toBe(`/jira/settings/personal/apps/${APP}/${ENV}`);
  });

  it('AC-008.1 gives nothing for a context that does not name the app', () => {
    expect(settingsPagePath({ localId: 'something-else' })).toBeNull();
    expect(settingsPagePath(undefined)).toBeNull();
  });
});
