import type { QueryClient } from '@tanstack/react-query';
import { act, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { AppProviders } from '@/app/providers/AppProviders';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import {
  resetSession,
  SESSION_QUERY_KEY,
  SESSION_STORAGE_PREFIX,
  setDevUser,
  useSignOut,
  type Session,
} from '@/shared/auth';
import { restoreLocation, stubLocationAssign } from '@/test/location';
import { createTestQueryClient, renderApp } from '@/test/render';

const KEYCLOAK_LOGOUT_URL =
  'https://auth.tsolve.io.vn/realms/tsolve/protocol/openid-connect/logout?id_token_hint=abc&post_logout_redirect_uri=https%3A%2F%2Fapp.tsolve.io.vn%2Flogin';

/** Session-only data a feature (e.g. the Chatbot history) keeps, and data that must survive logout. */
function storeBrowserData() {
  window.sessionStorage.setItem(`${SESSION_STORAGE_PREFIX}chatbot.history`, '[{"q":"VPN?"}]');
  window.localStorage.setItem(`${SESSION_STORAGE_PREFIX}draft`, 'x');
  window.localStorage.setItem('tsolve.ui.collapsed', 'true');
}

/** What the logout API answers in a test (the default mock is not used: its answer is the developer's to change). */
function logoutAnswers(resolver: () => Response | Promise<Response>) {
  server.use(http.post('*/api/v1/auth/logout', resolver));
}
const answer200 = (body: Record<string, unknown>) => () => HttpResponse.json(body);
const answer204 = () => new HttpResponse(null, { status: 204 });

async function logOut(fullName: string) {
  const user = userEvent.setup();
  // The button's name also holds its icons' labels: match the user's name inside it.
  await user.click(
    await screen.findByRole('button', { name: new RegExp(fullName) }, { timeout: 5000 }),
  );
  await user.click(await screen.findByText('Log out'));
}

function expectClearedLocally(queryClient: QueryClient) {
  expect(queryClient.getQueryData(['tickets', 'cached'])).toBeUndefined();
  expect(queryClient.getMutationCache().getAll()).toEqual([]);
  expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull();
  expect(window.sessionStorage.getItem(`${SESSION_STORAGE_PREFIX}chatbot.history`)).toBeNull();
  expect(window.localStorage.getItem(`${SESSION_STORAGE_PREFIX}draft`)).toBeNull();
  expect(window.localStorage.getItem('tsolve.ui.collapsed')).toBe('true');
}

function expectNoErrorShown() {
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.queryByText(/MSG\d+/)).not.toBeInTheDocument();
}

/** The app must go to Login inside the app: cleared, Login page, no page load, no error. */
async function expectLoginInApp(
  assign: ReturnType<typeof stubLocationAssign>,
  router: ReturnType<typeof renderApp>['router'],
  queryClient: QueryClient,
) {
  await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
  expect(
    await screen.findByRole('button', { name: /sign in with google/i }, { timeout: 5000 }),
  ).toBeInTheDocument();
  expectClearedLocally(queryClient);
  expect(assign).not.toHaveBeenCalled();
  expectNoErrorShown();
}

/** Opens `/search` as a Ticket Resolver with data to drop, and logs out. */
async function signedInThenLogOut() {
  storeBrowserData();
  const rendered = renderApp('/search', { role: 'TICKET_RESOLVER' });
  rendered.queryClient.setQueryData(['tickets', 'cached'], ['ITSUP-1031']);
  rendered.queryClient
    .getMutationCache()
    .build(rendered.queryClient, { mutationKey: ['tickets', 'reject'] });
  await logOut(MOCK_USERS.tr.user.fullName);
  return rendered;
}

describe('log out', () => {
  let assign: ReturnType<typeof stubLocationAssign>;

  beforeEach(() => {
    assign = stubLocationAssign();
  });
  afterEach(() => {
    restoreLocation();
    window.sessionStorage.clear();
  });

  it('AC-008.1 calls the logout API, then drops all cached and session-only data of the user', async () => {
    let logoutCalls = 0;
    logoutAnswers(() => {
      logoutCalls++;
      return HttpResponse.json({ logoutUrl: KEYCLOAK_LOGOUT_URL });
    });
    storeBrowserData();
    const { queryClient } = renderApp('/search', { role: 'TICKET_RESOLVER' });
    queryClient.setQueryData(['tickets', 'cached'], ['ITSUP-1031']);
    queryClient.getMutationCache().build(queryClient, { mutationKey: ['tickets', 'reject'] });
    expect(queryClient.getMutationCache().getAll()).toHaveLength(1);

    await logOut(MOCK_USERS.tr.user.fullName);

    await waitFor(() => expect(logoutCalls).toBe(1));
    await waitFor(() => expect(assign).toHaveBeenCalled());
    expectClearedLocally(queryClient);
  });

  it('AC-008.1 asks the server first and clears afterwards (order)', async () => {
    const holder: { queryClient?: QueryClient } = {};
    const seenWhileInFlight: { query?: unknown; storage?: string | null } = {};
    logoutAnswers(() => {
      seenWhileInFlight.query = holder.queryClient?.getQueryData(['tickets', 'cached']);
      seenWhileInFlight.storage = window.sessionStorage.getItem(
        `${SESSION_STORAGE_PREFIX}chatbot.history`,
      );
      return HttpResponse.json({ logoutUrl: KEYCLOAK_LOGOUT_URL });
    });
    storeBrowserData();
    const rendered = renderApp('/search', { role: 'TICKET_RESOLVER' });
    const queryClient = rendered.queryClient;
    holder.queryClient = queryClient;
    queryClient.setQueryData(['tickets', 'cached'], ['ITSUP-1031']);

    await logOut(MOCK_USERS.tr.user.fullName);

    await waitFor(() => expect(assign).toHaveBeenCalled());
    expect(seenWhileInFlight.query).toEqual(['ITSUP-1031']);
    expect(seenWhileInFlight.storage).toBe('[{"q":"VPN?"}]');
    expect(queryClient.getQueryData(['tickets', 'cached'])).toBeUndefined();
    expect(window.sessionStorage.getItem(`${SESSION_STORAGE_PREFIX}chatbot.history`)).toBeNull();
  });

  it('AC-008.1 a user who signs in after a logout sees no data of the previous user', async () => {
    logoutAnswers(answer204);
    storeBrowserData();
    const { router, queryClient } = renderApp('/search', { role: 'TICKET_RESOLVER' });
    queryClient.setQueryData(['tickets', 'cached'], ['ITSUP-1031']);
    await logOut(MOCK_USERS.tr.user.fullName);
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));

    // Sign in the Knowledge Manager as the dev user switcher does (a test shortcut; the Login page itself is built).
    await act(async () => {
      setDevUser('km');
      await resetSession(queryClient);
      await router.navigate('/');
    });

    expect(
      await screen.findByRole(
        'button',
        { name: new RegExp(MOCK_USERS.km.user.fullName) },
        { timeout: 5000 },
      ),
    ).toBeInTheDocument();
    expect(queryClient.getQueryData<Session>(SESSION_QUERY_KEY)?.user.id).toBe(
      MOCK_USERS.km.user.id,
    );
    expect(queryClient.getQueryData(['tickets', 'cached'])).toBeUndefined();
    expect(window.sessionStorage.getItem(`${SESSION_STORAGE_PREFIX}chatbot.history`)).toBeNull();
    expect(window.localStorage.getItem(`${SESSION_STORAGE_PREFIX}draft`)).toBeNull();
    expect(screen.queryByText(MOCK_USERS.tr.user.fullName)).not.toBeInTheDocument();
  });

  it('AC-008.2 navigates the browser to logoutUrl with a full page load once the data is dropped', async () => {
    const holder: { queryClient?: QueryClient } = {};
    const atTheCall: { query?: unknown; storage?: string | null; session?: unknown } = {};
    restoreLocation();
    assign = stubLocationAssign(() => {
      atTheCall.query = holder.queryClient?.getQueryData(['tickets', 'cached']);
      atTheCall.storage = window.sessionStorage.getItem(`${SESSION_STORAGE_PREFIX}chatbot.history`);
      atTheCall.session = holder.queryClient?.getQueryData(SESSION_QUERY_KEY);
    });
    logoutAnswers(answer200({ logoutUrl: KEYCLOAK_LOGOUT_URL }));
    storeBrowserData();
    const rendered = renderApp('/search', { role: 'TICKET_RESOLVER' });
    const queryClient = rendered.queryClient;
    holder.queryClient = queryClient;
    queryClient.setQueryData(['tickets', 'cached'], ['ITSUP-1031']);

    await logOut(MOCK_USERS.tr.user.fullName);

    await waitFor(() => expect(assign).toHaveBeenCalledTimes(1));
    expect(assign).toHaveBeenCalledWith(KEYCLOAK_LOGOUT_URL);
    expect(atTheCall).toEqual({ query: undefined, storage: null, session: null });
    expectNoErrorShown();
  });

  it('AC-008.3 still signs out locally and shows Login in the app when the logout request fails', async () => {
    let meCalls = 0;
    server.use(
      http.post('*/api/v1/auth/logout', () => problem(500)),
      // Counts the requests and leaves the answer to the /me mock.
      http.get('*/api/v1/me', () => {
        meCalls++;
      }),
    );
    storeBrowserData();
    const { router, queryClient } = renderApp('/search', { role: 'TICKET_RESOLVER' });
    await screen.findByRole(
      'button',
      { name: new RegExp(MOCK_USERS.tr.user.fullName) },
      { timeout: 5000 },
    );
    const meCallsBeforeLogout = meCalls;
    expect(meCallsBeforeLogout).toBeGreaterThan(0);

    await logOut(MOCK_USERS.tr.user.fullName);

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(
      await screen.findByRole('button', { name: /sign in with google/i }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull();
    expect(window.sessionStorage.getItem(`${SESSION_STORAGE_PREFIX}chatbot.history`)).toBeNull();
    // The server session may still be valid; the app must not sign the user back in by refetching /me.
    expect(meCalls).toBe(meCallsBeforeLogout);
    expect(assign).not.toHaveBeenCalled();
  });

  it('AC-008.4 204 (old answer): clears everything, goes to Login in the app, shows no error', async () => {
    logoutAnswers(answer204);
    const { router, queryClient } = await signedInThenLogOut();

    await expectLoginInApp(assign, router, queryClient);
  });

  it('AC-008.4 200 without logoutUrl (empty object): clears everything, goes to Login, shows no error', async () => {
    logoutAnswers(answer200({}));
    const { router, queryClient } = await signedInThenLogOut();

    await expectLoginInApp(assign, router, queryClient);
  });

  it.each([
    ['a number', 42],
    ['null', null],
    ['an object', { href: 'https://auth.tsolve.io.vn/' }],
  ])('edge 1 logoutUrl that is not a string (%s) is not used', async (_name, logoutUrl) => {
    logoutAnswers(answer200({ logoutUrl }));
    const { router, queryClient } = await signedInThenLogOut();

    await expectLoginInApp(assign, router, queryClient);
  });

  it.each(['/login', '//evil.example', 'logout'])(
    'edge 2 relative or protocol-relative logoutUrl (%s) is not used',
    async (logoutUrl) => {
      logoutAnswers(answer200({ logoutUrl }));
      const { router, queryClient } = await signedInThenLogOut();

      await expectLoginInApp(assign, router, queryClient);
    },
  );

  it.each(['javascript:alert(1)', 'data:text/html,x', 'ftp://host/x', 'file:///etc/passwd'])(
    'edge 3 logoutUrl with another scheme (%s) is not used',
    async (logoutUrl) => {
      logoutAnswers(answer200({ logoutUrl }));
      const { router, queryClient } = await signedInThenLogOut();

      await expectLoginInApp(assign, router, queryClient);
      for (const [url] of assign.mock.calls) expect(url).not.toMatch(/^javascript:/i);
    },
  );
});

describe('useSignOut result', () => {
  let assign: ReturnType<typeof stubLocationAssign>;

  beforeEach(() => {
    assign = stubLocationAssign();
  });
  afterEach(() => {
    restoreLocation();
    window.sessionStorage.clear();
  });

  function setup() {
    const queryClient = createTestQueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <AppProviders queryClient={queryClient}>{children}</AppProviders>
    );
    return renderHook(() => useSignOut(), { wrapper });
  }

  it.each([
    ['a valid URL', () => HttpResponse.json({ logoutUrl: KEYCLOAK_LOGOUT_URL }), true],
    ['a 500 answer', () => problem(500), false],
    ['a network error', () => HttpResponse.error(), false],
    ['a 204 answer', answer204, false],
    ['an empty logoutUrl', () => HttpResponse.json({ logoutUrl: '' }), false],
  ])(
    'edge 4 useSignOut returns true when it navigated away and false otherwise, and never throws (%s)',
    async (_name, resolver, navigated) => {
      logoutAnswers(resolver);
      const { result } = setup();

      let returned: unknown;
      await act(async () => {
        returned = await result.current();
      });

      expect(returned).toBe(navigated);
      expect(assign).toHaveBeenCalledTimes(navigated ? 1 : 0);
    },
  );
});
