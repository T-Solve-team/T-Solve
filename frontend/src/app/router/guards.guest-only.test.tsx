import { waitFor } from '@testing-library/react';
import { http as mock } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { renderApp } from '@/test/render';

/**
 * AC-012.5: the cookie of another Workspace site (401 MSG123 from /me) sends the user to Login with
 * `error=MSG123` — also when the user opened Login directly, not only through a protected route.
 */
describe('GuestOnly with the cookie of another Workspace site', () => {
  it.each(['/login', '/login?returnTo=%2Freview-queue'])(
    'AC-012.5 %s with /me answering 401 MSG123 ends on /login?error=MSG123',
    async (url) => {
      server.use(mock.get('*/api/v1/me', () => problem(401, 'MSG123')));
      const { router } = renderApp(url, { role: null });

      await waitFor(
        () => expect(new URLSearchParams(router.state.location.search).get('error')).toBe('MSG123'),
        { timeout: 5000 },
      );
      expect(router.state.location.pathname).toBe('/login');
      expect(router.state.location.search).toBe('?error=MSG123');
    },
  );

  it('AC-012.5 /login?error=MSG123 stays where it is', async () => {
    server.use(mock.get('*/api/v1/me', () => problem(401, 'MSG123')));
    const { router } = renderApp('/login?error=MSG123', { role: null });

    await waitFor(() => expect(router.state.location.search).toBe('?error=MSG123'), {
      timeout: 5000,
    });
    // Let a redirect loop show itself, if there were one.
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.search).toBe('?error=MSG123');
  });
});
