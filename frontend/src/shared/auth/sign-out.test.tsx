import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import {
  resetSession,
  SESSION_QUERY_KEY,
  SESSION_STORAGE_PREFIX,
  setDevUser,
  type Session,
} from '@/shared/auth';
import { renderApp } from '@/test/render';

/** Session-only data a feature (e.g. the Chatbot history) keeps, and data that must survive logout. */
function storeBrowserData() {
  window.sessionStorage.setItem(`${SESSION_STORAGE_PREFIX}chatbot.history`, '[{"q":"VPN?"}]');
  window.localStorage.setItem(`${SESSION_STORAGE_PREFIX}draft`, 'x');
  window.localStorage.setItem('tsolve.ui.collapsed', 'true');
}

async function logOut(fullName: string) {
  const user = userEvent.setup();
  // The button's name also holds its icons' labels: match the user's name inside it.
  await user.click(
    await screen.findByRole('button', { name: new RegExp(fullName) }, { timeout: 5000 }),
  );
  await user.click(await screen.findByText('Log out'));
}

describe('log out', () => {
  afterEach(() => window.sessionStorage.clear());

  it('AC-008.1 calls the logout API and drops all cached and session-only data of the user', async () => {
    let logoutCalls = 0;
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        logoutCalls++;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    storeBrowserData();
    const { queryClient } = renderApp('/search', { role: 'TICKET_RESOLVER' });
    queryClient.setQueryData(['tickets', 'cached'], ['ITSUP-1031']);
    queryClient.getMutationCache().build(queryClient, { mutationKey: ['tickets', 'reject'] });
    expect(queryClient.getMutationCache().getAll()).toHaveLength(1);

    await logOut(MOCK_USERS.tr.user.fullName);

    await waitFor(() => expect(logoutCalls).toBe(1));
    expect(queryClient.getQueryData(['tickets', 'cached'])).toBeUndefined();
    expect(queryClient.getMutationCache().getAll()).toEqual([]);
    expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull();
    expect(window.sessionStorage.getItem(`${SESSION_STORAGE_PREFIX}chatbot.history`)).toBeNull();
    expect(window.localStorage.getItem(`${SESSION_STORAGE_PREFIX}draft`)).toBeNull();
    expect(window.localStorage.getItem('tsolve.ui.collapsed')).toBe('true');
  });

  it('AC-008.1 a user who signs in after a logout sees no data of the previous user', async () => {
    storeBrowserData();
    const { router, queryClient } = renderApp('/search', { role: 'TICKET_RESOLVER' });
    queryClient.setQueryData(['tickets', 'cached'], ['ITSUP-1031']);
    await logOut(MOCK_USERS.tr.user.fullName);
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));

    // Sign-in is not built yet: sign the Knowledge Manager in as the dev user switcher does.
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

  it('AC-008.2 sends the user to Login when logout finishes', async () => {
    const { router } = renderApp('/search', { role: 'TICKET_RESOLVER' });
    await logOut(MOCK_USERS.tr.user.fullName);

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
  });

  it('AC-008.3 still signs out locally and shows Login when the logout request fails', async () => {
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
      await screen.findByText(/^1\.1\.1 Login — not built yet/, undefined, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull();
    expect(window.sessionStorage.getItem(`${SESSION_STORAGE_PREFIX}chatbot.history`)).toBeNull();
    // The server session may still be valid; the app must not sign the user back in by refetching /me.
    expect(meCalls).toBe(meCallsBeforeLogout);
  });
});
