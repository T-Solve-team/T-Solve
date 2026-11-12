import { getWorkspace, setupWorkspace, updateWorkspace } from '@/shared/api';
import { setDevUser } from '@/shared/auth';

const SETUP = {
  companyName: 'Example Company',
  jiraSiteUrl: 'https://company.atlassian.net',
  serviceAccountEmail: 'service@company.example',
  serviceAccountToken: 'fake-test-api-token',
};

describe('Workspace mock contracts', () => {
  beforeEach(() => setDevUser('admin'));

  it('AC-005.5 starts without setup, then stores Setup for GET and PUT without exposing the token', async () => {
    await expect(getWorkspace()).rejects.toMatchObject({ status: 404 });
    const info = await setupWorkspace(SETUP);
    expect(info).toEqual({
      companyName: SETUP.companyName,
      jiraSiteUrl: SETUP.jiraSiteUrl,
      serviceAccountEmail: SETUP.serviceAccountEmail,
    });
    expect(await getWorkspace()).toEqual(info);
    const changed = await updateWorkspace({
      companyName: 'Renamed Company',
      serviceAccountEmail: SETUP.serviceAccountEmail,
    });
    expect(changed).toEqual({ ...info, companyName: 'Renamed Company' });
    expect(await getWorkspace()).toEqual(changed);
  });

  it('AC-005.6 refuses a second setup without overwriting the Workspace', async () => {
    const info = await setupWorkspace(SETUP);
    await expect(
      setupWorkspace({ ...SETUP, companyName: 'Another Company' }),
    ).rejects.toMatchObject({ status: 409 });
    expect(await getWorkspace()).toEqual(info);
  });

  it('AC-005.3 rejects missing setup fields without storing a Workspace', async () => {
    await expect(setupWorkspace({ ...SETUP, serviceAccountToken: ' ' })).rejects.toMatchObject({
      status: 400,
      fieldErrors: [{ field: 'serviceAccountToken', code: 'MSG01' }],
    });
    await expect(getWorkspace()).rejects.toMatchObject({ status: 404 });
  });

  it('AC-005.4 returns MSG61 under the site field without storing an invalid site', async () => {
    await expect(
      setupWorkspace({ ...SETUP, jiraSiteUrl: 'https://localhost' }),
    ).rejects.toMatchObject({
      status: 400,
      fieldErrors: [{ field: 'jiraSiteUrl', code: 'MSG61' }],
    });
    await expect(getWorkspace()).rejects.toMatchObject({ status: 404 });
  });
});
