import { screen } from '@testing-library/react';

/**
 * main.tsx decides at start-up whether mock mode runs (dev build + VITE_API_MOCKING=true). These tests load
 * the real main.tsx with each setting and a fake MSW worker.
 */
async function startApp(mocking: 'true' | 'false') {
  vi.resetModules();
  vi.stubEnv('VITE_API_MOCKING', mocking);
  const start = vi.fn(() => Promise.resolve());
  vi.doMock('@/mocks/browser', () => ({ worker: { start } }));
  document.body.innerHTML = '<div id="root"></div>';
  await import('@/main');
  return start;
}

describe('mock mode', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.doUnmock('@/mocks/browser');
    document.body.innerHTML = '';
  });

  it('AC-009.6 starts no mock server and shows no dev user switcher when mock mode is off', async () => {
    const start = await startApp('false');
    expect(await screen.findByRole('heading', { level: 1 }, { timeout: 5000 })).toBeInTheDocument();

    expect(start).not.toHaveBeenCalled();
    expect(screen.queryByRole('combobox', { name: 'Dev user' })).not.toBeInTheDocument();
  });

  it('AC-009.6 starts the mock server and shows the switcher when mock mode is on (control)', async () => {
    const start = await startApp('true');

    expect(start).toHaveBeenCalledTimes(1);
    expect(
      await screen.findByRole('combobox', { name: 'Dev user' }, { timeout: 5000 }),
    ).toBeInTheDocument();
  });
});
