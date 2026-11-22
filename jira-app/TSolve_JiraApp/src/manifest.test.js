/**
 * @jest-environment node
 */
// STORY-013: the manifest hands Forge's system token to the T-Solve backend. Jest's roots are src/, so this
// test sits here and reads the manifest one folder up.
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const manifest = yaml.load(fs.readFileSync(path.join(__dirname, '..', 'manifest.yml'), 'utf8'));
const ENDPOINT = 'tsolve-app-token-endpoint';

describe('manifest: app token for the backend', () => {
  it('AC-013.1 calls the app-token endpoint on install, on upgrade and every hour, with the system token', () => {
    const lifecycle = manifest.modules.trigger.find((t) => t.endpoint === ENDPOINT);
    expect(lifecycle.events).toEqual(['avi:forge:installed:app', 'avi:forge:upgraded:app']);
    const scheduled = manifest.modules.scheduledTrigger.filter((t) => t.endpoint === ENDPOINT);
    expect(scheduled).toHaveLength(1);
    expect(scheduled[0].interval).toBe('hour');
    const endpoint = manifest.modules.endpoint.find((e) => e.key === ENDPOINT);
    expect(endpoint.remote).toBe('tsolve-backend');
    expect(endpoint.route.path).toBe('/api/v1/jira-app/app-token');
    expect(endpoint.auth.appSystemToken.enabled).toBe(true);
  });

  it('AC-013.2 asks for read and write of Jira work and reading users, and no administration scope', () => {
    expect([...manifest.permissions.scopes].sort()).toEqual(['read:jira-user', 'read:jira-work', 'write:jira-work']);
    expect(manifest.permissions.scopes.some((scope) => scope.startsWith('manage:'))).toBe(false);
  });

  it('AC-014.1 adds the "T-Solve account" page to the Apps section', () => {
    const [page] = manifest.modules['jira:globalPage'];
    expect(manifest.modules['jira:personalSettingsPage']).toBeUndefined();
    expect(page).toMatchObject({
      key: 'tsolve-account-settings',
      resource: 'account-settings',
      render: 'native',
      title: 'T-Solve account',
      resolver: { endpoint: 'tsolve-ui-endpoint' },
    });
    expect(manifest.resources).toContainEqual({ key: 'account-settings', path: 'src/frontend/accountSettings/index.jsx' });
  });

  it('AC-013.3 goes straight to the remote, with no Forge function in between', () => {
    const endpoint = manifest.modules.endpoint.find((e) => e.key === ENDPOINT);
    expect(endpoint.function).toBeUndefined();
    expect(manifest.modules.function).toBeUndefined();
  });

  it('AC-005.6 sends every issue update straight to the issue-updated endpoint, as the issue-created trigger', () => {
    const trigger = manifest.modules.trigger.find((t) => t.key === 'tsolve-issue-updated-trigger');
    expect(trigger).toMatchObject({
      endpoint: 'tsolve-issue-updated-endpoint',
      events: ['avi:jira:updated:issue'],
      filter: { ignoreSelf: true },
    });
    const endpoint = manifest.modules.endpoint.find((e) => e.key === 'tsolve-issue-updated-endpoint');
    expect(endpoint).toEqual({
      key: 'tsolve-issue-updated-endpoint',
      remote: 'tsolve-backend',
      route: { path: '/api/v1/jira-app/events/issue-updated' },
    });
  });
});
