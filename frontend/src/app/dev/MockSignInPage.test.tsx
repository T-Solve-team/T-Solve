import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DEV_USERS, getDevSite, getDevUser, type DevUserId } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

/**
 * Q5: in mock mode "Sign in with Google" opens this dev-only page. The tests go through the route
 * (`/dev/sign-in`), never an import, so they also prove the route is registered. Names cite AC-012.1 / AC-011.6
 * (QA question 1, option A). The page's buttons are found by role "button" and these names.
 */
const OUTCOMES = {
  cancel: /cancel/i,
  unknown: /unknown account/i,
  deactivated: /deactivated account/i,
  wrongDomain: /wrong google domain/i,
  expired: /expired link/i,
  suspended: /suspended workspace/i,
} as const;

async function openMockSignIn(returnTo?: string) {
  const search = returnTo === undefined ? '' : `?${new URLSearchParams({ returnTo }).toString()}`;
  const rendered = renderApp(`/dev/sign-in${search}`, { user: null });
  await screen.findByRole('button', { name: OUTCOMES.cancel }, { timeout: 2500 });
  return { ...rendered, user: userEvent.setup() };
}

const here = (router: { state: { location: { pathname: string; search: string } } }) =>
  `${router.state.location.pathname}${router.state.location.search}`;

describe('Mock sign-in page', () => {
  it('AC-012.1 mock sign-in lists every dev user and the six outcomes', async () => {
    await openMockSignIn();

    for (const label of Object.values(DEV_USERS)) {
      expect(screen.getByRole('button', { name: label }), label).toBeInTheDocument();
    }
    for (const [name, pattern] of Object.entries(OUTCOMES)) {
      expect(screen.getByRole('button', { name: pattern }), name).toBeInTheDocument();
    }
  });

  it.each([
    ['employee', '/search?q=a'],
    ['admin', '/users'],
  ] as const satisfies readonly (readonly [DevUserId, string])[])(
    'AC-012.1 mock sign-in with dev user %s sets it and opens returnTo',
    async (id, returnTo) => {
      const { router, user } = await openMockSignIn(returnTo);

      await user.click(screen.getByRole('button', { name: DEV_USERS[id] }));

      await waitFor(() => expect(here(router)).toBe(returnTo));
      expect(getDevUser()).toBe(id);
    },
  );

  it.each([undefined, '//evil.example', '/\\evil', 'https://evil.example'])(
    'AC-012.1 mock sign-in opens the landing page when returnTo is missing or not a same-site path: %j',
    async (returnTo) => {
      const { router, user } = await openMockSignIn(returnTo);

      await user.click(screen.getByRole('button', { name: DEV_USERS.admin }));

      await waitFor(() => expect(router.state.location.pathname).toBe('/users'));
      expect(router.state.location.pathname.startsWith('/')).toBe(true);
      expect(here(router)).not.toContain('evil.example');
    },
  );

  it('AC-012.1 mock sign-in drops the cached queries when a dev user is chosen', async () => {
    const { router, queryClient, user } = await openMockSignIn('/users');
    queryClient.setQueryData(['seeded'], 'old user data');

    await user.click(screen.getByRole('button', { name: DEV_USERS.admin }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/users'));
    expect(queryClient.getQueryData(['seeded'])).toBeUndefined();
    expect(getDevUser()).toBe('admin');
  });

  it.each([
    ['cancel', 'MSG06'],
    ['unknown', 'MSG09'],
    ['deactivated', 'MSG10'],
    ['expired', 'MSG141'],
  ] as const)('AC-012.1 mock sign-in outcome %s opens /login with %s', async (outcome, code) => {
    const { router, user } = await openMockSignIn('/users');
    const before = getDevUser();

    await user.click(screen.getByRole('button', { name: OUTCOMES[outcome] }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.search).toBe(`?error=${code}`);
    expect(await screen.findByText(msg(code))).toBeInTheDocument();
    expect(getDevUser()).toBe(before);
  });

  it('AC-012.1 mock sign-in wrong Google domain opens /login with google_domain=company.example', async () => {
    const { router, user } = await openMockSignIn('/users');

    await user.click(screen.getByRole('button', { name: OUTCOMES.wrongDomain }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    const params = new URLSearchParams(router.state.location.search);
    expect(params.get('error')).toBe('MSG122');
    expect(params.get('google_domain')).toBe('company.example');
    expect(
      await screen.findByText(msg('MSG122', { google_domain: 'company.example' })),
    ).toBeInTheDocument();
  });

  it('AC-011.6 mock sign-in suspended Workspace sets the suspended site and shows the MSG120 full page', async () => {
    const { router, user } = await openMockSignIn('/users');

    await user.click(screen.getByRole('button', { name: OUTCOMES.suspended }));

    await waitFor(() => expect(getDevSite()).toBe('suspended-workspace'));
    await waitFor(() => expect(here(router)).toBe('/login?error=MSG120'));
    expect(await screen.findByText(msg('MSG120'))).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /sign in with google/i })).not.toBeInTheDocument();
  });
});
