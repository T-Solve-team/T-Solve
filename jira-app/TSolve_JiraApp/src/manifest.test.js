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

  it('AC-013.2 asks for read and write of Jira work', () => {
    expect(manifest.permissions.scopes).toEqual(
      expect.arrayContaining(['read:jira-work', 'write:jira-work']),
    );
  });

  it('AC-013.3 goes straight to the remote, with no Forge function in between', () => {
    const endpoint = manifest.modules.endpoint.find((e) => e.key === ENDPOINT);
    expect(endpoint.function).toBeUndefined();
    expect(manifest.modules.function).toBeUndefined();
  });
});
