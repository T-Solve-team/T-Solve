import { act, screen } from '@testing-library/react';
import type * as ReactDomClient from 'react-dom/client';

// main.tsx creates its own React root, which Testing Library's cleanup does not know about. Left mounted, the app
// keeps rendering (queries, antd motion) after the test, and React work that runs after jsdom is torn down fails
// the run with "window is not defined". So each root main.tsx creates is kept here and unmounted after the test.
const appRoots: ReactDomClient.Root[] = [];

/**
 * main.tsx decides at start-up whether mock mode runs (dev build + VITE_API_MOCKING=true). These tests load
 * the real main.tsx with each setting and a fake MSW worker.
 */
async function startApp(mocking: 'true' | 'false') {
  vi.resetModules();
  vi.stubEnv('VITE_API_MOCKING', mocking);
  const start = vi.fn(() => Promise.resolve());
  vi.doMock('@/mocks/browser', () => ({ worker: { start } }));
  vi.doMock('react-dom/client', async () => {
    const client = await vi.importActual<typeof ReactDomClient>('react-dom/client');
    const createRoot: typeof client.createRoot = (...args) => {
      const root = client.createRoot(...args);
      appRoots.push(root);
      return root;
    };
    return { ...client, createRoot };
  });
  document.body.innerHTML = '<div id="root"></div>';
  await import('@/main');
  return start;
}

describe('mock mode', () => {
  afterEach(() => {
    act(() => appRoots.splice(0).forEach((root) => root.unmount()));
    vi.unstubAllEnvs();
    vi.doUnmock('@/mocks/browser');
    vi.doUnmock('react-dom/client');
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
