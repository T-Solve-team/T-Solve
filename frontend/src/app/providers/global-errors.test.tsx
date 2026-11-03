import type { QueryClient } from '@tanstack/react-query';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http as mock } from 'msw';
import { server } from '@/mocks/server';
import { approveTickets, getReviewQueue } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { SESSION_QUERY_KEY, setDevUser } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const SESSION_ENTRY = 'tsolve.session.test-entry';

async function openReviewQueue() {
  const rendered = renderApp('/review-queue');
  // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
  await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, { timeout: 5000 });
  return rendered;
}

/** Records whether a dialog was ever added to the page from now on (even one closed again at once). */
function watchDialogs() {
  let seen = false;
  const observer = new MutationObserver((records) => {
    for (const record of records) {
      for (const node of record.addedNodes) {
        if (
          node instanceof Element &&
          (node.matches('[role="dialog"]') || node.querySelector('[role="dialog"]'))
        )
          seen = true;
      }
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
  return {
    seen: () => seen || document.querySelector('[role="dialog"]') !== null,
    stop: () => observer.disconnect(),
  };
}

function request(client: QueryClient, key: string, page?: number) {
  return client
    .fetchQuery({
      queryKey: [key, page],
      queryFn: () => getReviewQueue(page === undefined ? undefined : { page }),
    })
    .catch(() => undefined);
}

/** A mutation that keeps quiet about its own errors, so the mutation cache is not empty. */
function quietMutation(client: QueryClient) {
  return client
    .getMutationCache()
    .build(client, {
      mutationFn: () => approveTickets({ ticketIds: ['20000000-0000-4000-8000-000000000001'] }),
      meta: { handlesOwnErrors: true },
    })
    .execute(undefined)
    .catch(() => undefined);
}

async function expectLoginWithMsg123(router: ReturnType<typeof renderApp>['router']) {
  await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
  expect(new URLSearchParams(router.state.location.search).get('error')).toBe('MSG123');
}

/** As at logout (AC-008.1): session signed out, every other query and mutation and tsolve.session.* gone. */
function expectSignedOutAsAtLogout(client: QueryClient) {
  expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull();
  const queues = client.getQueriesData({ queryKey: ['/api/v1/review-queue'] });
  expect(queues.every(([, data]) => data === undefined)).toBe(true);
  expect(client.getMutationCache().getAll()).toHaveLength(0);
  expect(window.sessionStorage.getItem(SESSION_ENTRY)).toBeNull();
}

afterEach(() => {
  window.sessionStorage.clear();
});

describe('global error handling', () => {
  it('shows a failed mutation as a toast with its MSG code (BUG-003)', async () => {
    server.use(mock.post('*/api/v1/reviews/approve', () => problem(403)));
    const { queryClient } = renderApp('/review-queue');
    // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
    await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, { timeout: 5000 });

    await queryClient
      .getMutationCache()
      .build(queryClient, {
        mutationFn: () => approveTickets({ ticketIds: ['20000000-0000-4000-8000-000000000001'] }),
      })
      .execute(undefined)
      .catch(() => undefined);

    expect(await screen.findByText(msg('MSG08'))).toBeInTheDocument();
  });

  it('stays quiet for a mutation that shows its own error', async () => {
    server.use(mock.post('*/api/v1/reviews/approve', () => problem(409, 'MSG49')));
    const { queryClient } = renderApp('/review-queue');
    await screen.findByRole('heading', { level: 1, name: 'Review Queue' });

    await queryClient
      .getMutationCache()
      .build(queryClient, {
        mutationFn: () => approveTickets({ ticketIds: ['20000000-0000-4000-8000-000000000001'] }),
        meta: { handlesOwnErrors: true },
      })
      .execute(undefined)
      .catch(() => undefined);

    expect(screen.queryByText(msg('MSG49'))).not.toBeInTheDocument();
  });

  it('on 401 shows MSG07, then sends the user to Login with returnTo', async () => {
    const user = userEvent.setup();
    const { queryClient, router } = renderApp('/review-queue');
    await screen.findByRole('heading', { level: 1, name: 'Review Queue' });

    // The session expires on the server: every request now answers 401.
    setDevUser(null);
    server.use(mock.get('*/api/v1/review-queue', () => problem(401)));
    await queryClient
      .fetchQuery({ queryKey: ['expired'], queryFn: () => getReviewQueue() })
      .catch(() => undefined);

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG07'));
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.search).toBe(`?returnTo=${encodeURIComponent('/review-queue')}`);
  });

  it('shows MSG06 when the session cannot be loaded at all', async () => {
    server.use(mock.get('*/api/v1/me', () => problem(500)));
    renderApp('/review-queue');
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('logs out through the API and lands on Login', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/review-queue');
    await user.click(await screen.findByRole('button', { name: /Đỗ Minh Quân/ }));
    await user.click(await screen.findByText('Log out'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
  });
});

// These run after the MSG07 tests above: today a 401 MSG123 opens the MSG07 dialog, which no one acknowledges.
describe('401 with MSG123 (a session of another Workspace site)', () => {
  it('edge 25 MSG123 while the MSG07 dialog is open closes the dialog and takes the MSG123 path', async () => {
    const { queryClient, router } = await openReviewQueue();

    server.use(mock.get('*/api/v1/review-queue', () => problem(401)));
    await request(queryClient, 'expired');
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG07'));

    server.use(mock.get('*/api/v1/review-queue', () => problem(401, 'MSG123')));
    await request(queryClient, 'other-site');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await expectLoginWithMsg123(router);
  });

  it('AC-012.5 on a 401 MSG123 from a query drops the session, cache and tsolve.session. entries and goes to Login with error=MSG123 without MSG07', async () => {
    const { queryClient, router } = await openReviewQueue();
    await quietMutation(queryClient);
    window.sessionStorage.setItem(SESSION_ENTRY, 'chat history');
    const dialogs = watchDialogs();

    server.use(mock.all('*/api/v1/*', () => problem(401, 'MSG123')));
    await request(queryClient, 'other-site');

    await expectLoginWithMsg123(router);
    expectSignedOutAsAtLogout(queryClient);
    expect(dialogs.seen()).toBe(false);
    dialogs.stop();
  });

  it('AC-012.5 the same for a mutation answering 401 MSG123', async () => {
    const { queryClient, router } = await openReviewQueue();
    window.sessionStorage.setItem(SESSION_ENTRY, 'chat history');
    const dialogs = watchDialogs();

    server.use(mock.post('*/api/v1/reviews/approve', () => problem(401, 'MSG123')));
    await queryClient
      .getMutationCache()
      .build(queryClient, {
        mutationFn: () => approveTickets({ ticketIds: ['20000000-0000-4000-8000-000000000001'] }),
      })
      .execute(undefined)
      .catch(() => undefined);

    await expectLoginWithMsg123(router);
    expectSignedOutAsAtLogout(queryClient);
    expect(dialogs.seen()).toBe(false);
    dialogs.stop();
  });

  it('AC-012.5 /me answering 401 MSG123 on the first load goes to Login with error=MSG123', async () => {
    const dialogs = watchDialogs();
    server.use(mock.get('*/api/v1/me', () => problem(401, 'MSG123')));
    const { router } = renderApp('/review-queue');

    await waitFor(
      () => expect(new URLSearchParams(router.state.location.search).get('error')).toBe('MSG123'),
      { timeout: 5000 },
    );
    expect(router.state.location.pathname).toBe('/login');
    expect(dialogs.seen()).toBe(false);
    dialogs.stop();
  });

  it('edge 25 a 401 MSG123 with other 401s at once takes the MSG123 path with no MSG07 dialog', async () => {
    const { queryClient, router } = await openReviewQueue();
    const dialogs = watchDialogs();

    // page=1 is a plain 401 that arrives 50 ms after the MSG123.
    server.use(
      mock.get('*/api/v1/review-queue', async ({ request: sent }) => {
        if (new URL(sent.url).searchParams.get('page') === '1') {
          await delay(50);
          return problem(401);
        }
        return problem(401, 'MSG123');
      }),
    );
    await Promise.all([request(queryClient, 'first'), request(queryClient, 'second', 1)]);

    await expectLoginWithMsg123(router);
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(dialogs.seen()).toBe(false);
    await expectLoginWithMsg123(router);
    dialogs.stop();
  });
});
