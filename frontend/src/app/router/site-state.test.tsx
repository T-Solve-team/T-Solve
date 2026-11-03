import type { QueryClient } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import { http as mock } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import { approveTickets, getReviewQueue } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { SESSION_QUERY_KEY } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

/**
 * STORY-011: an unknown site (MSG119, 404) or a Suspended Workspace (MSG120, 403) replaces every screen of the
 * Workspace app with one full page — no menu, no user menu, no Try again — and drops the signed-in user's cache.
 */
const SITE_ANSWERS = [
  ['MSG119', 404],
  ['MSG120', 403],
] as const;

const PM_NAME = new RegExp(MOCK_USERS.pm.user.fullName);

async function openReviewQueue() {
  const rendered = renderApp('/review-queue');
  // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
  await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, { timeout: 5000 });
  return rendered;
}

/** From now on every API request answers like the tenant resolver does for this site. */
function siteAnswers(status: number, code: 'MSG119' | 'MSG120') {
  server.use(mock.all('*/api/v1/*', () => problem(status, code)));
}

/** A later request of the open screen (a refetch, another query…). */
function laterRequest(client: QueryClient, key = 'later') {
  return client
    .fetchQuery({ queryKey: [key], queryFn: () => getReviewQueue() })
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

function expectNoShell() {
  expect(screen.queryByRole('menu', { name: 'Main menu' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: PM_NAME })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  expect(screen.queryByRole('heading', { level: 1, name: 'Review Queue' })).not.toBeInTheDocument();
}

function expectCacheDropped(client: QueryClient) {
  expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull();
  const queues = client.getQueriesData({ queryKey: ['/api/v1/review-queue'] });
  expect(queues.every(([, data]) => data === undefined)).toBe(true);
  expect(client.getMutationCache().getAll()).toHaveLength(0);
}

describe('site state (unknown site, Suspended Workspace)', () => {
  it('AC-011.3 shows MSG119 in place of a Workspace screen with no menu, no user menu and no Try again', async () => {
    renderApp('/review-queue', { site: 'unknown' });

    expect(await screen.findByText(msg('MSG119'), {}, { timeout: 5000 })).toBeInTheDocument();
    expectNoShell();
  });

  it('AC-011.3 shows MSG119 in place of Login too', async () => {
    renderApp('/login', { role: null, site: 'unknown' });

    expect(await screen.findByText(msg('MSG119'), {}, { timeout: 5000 })).toBeInTheDocument();
    // The Login screen (1.1.1) is not shown on an unknown site.
    expect(screen.queryByText(/1\.1\.1 Login/)).not.toBeInTheDocument();
    expectNoShell();
  });

  it('AC-011.3 shows MSG119 when a later request answers it', async () => {
    const { queryClient } = await openReviewQueue();

    siteAnswers(404, 'MSG119');
    await laterRequest(queryClient);

    expect(await screen.findByText(msg('MSG119'))).toBeInTheDocument();
    expectNoShell();
  });

  it('AC-011.4 shows MSG120 for a signed-out user', async () => {
    renderApp('/login', { role: null, site: 'suspended' });

    expect(await screen.findByText(msg('MSG120'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByText(/1\.1\.1 Login/)).not.toBeInTheDocument();
    expectNoShell();
  });

  it('AC-011.4 shows MSG120 for a signed-in user in the same way', async () => {
    const { queryClient } = await openReviewQueue();

    siteAnswers(403, 'MSG120');
    await laterRequest(queryClient);

    expect(await screen.findByText(msg('MSG120'))).toBeInTheDocument();
    expectNoShell();
  });

  it.each(SITE_ANSWERS)(
    'AC-011.5 drops every cached query and mutation when %s arrives while signed in',
    async (code, status) => {
      const { queryClient } = await openReviewQueue();
      await quietMutation(queryClient);
      expect(queryClient.getMutationCache().getAll().length).toBeGreaterThan(0);

      siteAnswers(status, code);
      await laterRequest(queryClient);

      expect(await screen.findByText(msg(code))).toBeInTheDocument();
      await waitFor(() => expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull());
      expectCacheDropped(queryClient);
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      // Only the page: no toast with the same text.
      expect(screen.getAllByText(msg(code))).toHaveLength(1);
    },
  );

  it('edge 23 a mutation answering MSG120 while the user works shows the suspended page with no toast', async () => {
    const { queryClient } = await openReviewQueue();

    server.use(mock.post('*/api/v1/reviews/approve', () => problem(403, 'MSG120')));
    await queryClient
      .getMutationCache()
      .build(queryClient, {
        mutationFn: () => approveTickets({ ticketIds: ['20000000-0000-4000-8000-000000000001'] }),
      })
      .execute(undefined)
      .catch(() => undefined);

    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { level: 1, name: 'Review Queue' }),
      ).not.toBeInTheDocument(),
    );
    expect(screen.getAllByText(msg('MSG120'))).toHaveLength(1);
    expectNoShell();
    await waitFor(() => expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull());
    expectCacheDropped(queryClient);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('edge 24 several requests answering MSG119 at once show one page and reset the session once', async () => {
    const { queryClient } = await openReviewQueue();
    const clear = vi.spyOn(queryClient.getMutationCache(), 'clear');

    siteAnswers(404, 'MSG119');
    await Promise.all([1, 2, 3].map((n) => laterRequest(queryClient, `later-${n}`)));

    expect(await screen.findAllByText(msg('MSG119'))).toHaveLength(1);
    await waitFor(() => expect(clear).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(clear).toHaveBeenCalledTimes(1);
    expect(screen.getAllByText(msg('MSG119'))).toHaveLength(1);
  });

  it.each(SITE_ANSWERS)(
    'edge 26 /me answering %s on the first load shows the site page, not the session error page',
    async (code, status) => {
      server.use(mock.get('*/api/v1/me', () => problem(status, code)));
      renderApp('/review-queue');

      expect(await screen.findByText(msg(code), {}, { timeout: 5000 })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
      expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    },
  );
});
