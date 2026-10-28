import { screen, waitFor } from '@testing-library/react';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { featureRoutes } from './feature-routes';

const CONSOLE = { level: 1, name: 'Platform Console' } as const;
const NOT_FOUND = 'This page does not exist.';

/** The first findBy also loads a lazy module: give it more time (guideline 10 §6). */
const slow = { timeout: 5000 };

describe('route tree by host', () => {
  it('AC-013.3 shows the Platform Console placeholder at / on the Platform Console host', async () => {
    const { router } = renderApp('/', { host: 'admin.localhost', role: null });
    expect(await screen.findByRole('heading', CONSOLE, slow)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });

  it('AC-013.1 gives the Platform Console host no Workspace screen, menu or landing page', async () => {
    const { router } = renderApp('/', { host: 'admin.tsolve.io.vn', role: 'ADMIN' });
    expect(await screen.findByRole('heading', CONSOLE, slow)).toBeInTheDocument();
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });

  it.each([
    '/login',
    '/profile',
    '/users',
    '/review-queue',
    '/dashboard',
    '/search',
    '/workspace-setup',
  ])('AC-013.1 opens no Workspace path on the Platform Console host (%s)', async (path) => {
    const { router } = renderApp(path, { host: 'admin.localhost', role: null });
    expect(await screen.findByText(NOT_FOUND, {}, slow)).toBeInTheDocument();
    expect(screen.getByText('404')).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG08'))).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe(path);
  });

  it('AC-013.2 uses the Workspace routes on a Workspace host and shows no Console screen', async () => {
    const { router } = renderApp('/', { host: 'acme.localhost', role: 'PROJECT_MANAGER' });
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, slow),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/review-queue');
    expect(screen.queryByRole('heading', CONSOLE)).not.toBeInTheDocument();
  });

  it('AC-013.2 uses the Workspace routes when no host is given (localhost)', async () => {
    renderApp('/login', { role: null });
    expect(
      await screen.findByRole('button', { name: /sign in with google/i }, slow),
    ).toBeInTheDocument();
    expect(screen.queryByRole('heading', CONSOLE)).not.toBeInTheDocument();
  });

  it('AC-013.2 registers no route of features/platform-console in the Workspace routes', async () => {
    expect(featureRoutes.map((route) => route.title)).not.toContain('Platform Console');
    expect(featureRoutes.map((route) => route.path)).not.toContain('/platform-console');
    renderApp('/', { host: 'acme.localhost', role: 'PROJECT_MANAGER' });
    await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, slow);
    expect(screen.queryByText('Platform Console')).not.toBeInTheDocument();
  });

  it('AC-013.4 shows the 404 page for a path no Platform Console route matches', async () => {
    renderApp('/no-such-page', { host: 'admin.localhost' });
    expect(await screen.findByText(NOT_FOUND, {}, slow)).toBeInTheDocument();
    expect(screen.getByText('404')).toBeInTheDocument();
  });

  it.each([null, 'ADMIN'] as const)(
    'edge 29 shows 404, not a Workspace screen or a 403, for /users on admin.tsolve.io.vn (role %s)',
    async (role) => {
      const { router } = renderApp('/users', { host: 'admin.tsolve.io.vn', role });
      expect(await screen.findByText(NOT_FOUND, {}, slow)).toBeInTheDocument();
      expect(screen.queryByText(msg('MSG08'))).not.toBeInTheDocument();
      expect(
        screen.queryByRole('heading', { level: 1, name: 'User List' }),
      ).not.toBeInTheDocument();
      await waitFor(() => expect(router.state.location.pathname).toBe('/users'));
    },
  );

  it.each(['ADMIN.localhost', 'Admin.Tsolve.io.vn'])(
    'edge 30 compares the host lower-cased: the route tree of %s is the Console',
    async (host) => {
      renderApp('/', { host, role: null });
      expect(await screen.findByRole('heading', CONSOLE, slow)).toBeInTheDocument();
    },
  );

  it.each(['admin.localhost.', 'ADMIN.localhost.'])(
    'edge 30 ignores one trailing dot in the host: the route tree of %s is the Console',
    async (host) => {
      renderApp('/', { host, role: null });
      expect(await screen.findByRole('heading', CONSOLE, slow)).toBeInTheDocument();
    },
  );
});
