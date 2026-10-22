import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import type { DevUserId } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

/** `/me` answers this fixture user with a Workspace that has no company information yet (UC-10). */
function workspaceNotSetUp(user: DevUserId) {
  server.use(
    http.get('*/api/v1/me', () =>
      HttpResponse.json({ ...MOCK_USERS[user], workspaceReady: false }),
    ),
  );
}

const heading = (name: string) =>
  // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
  screen.findByRole('heading', { level: 1, name }, { timeout: 5000 });

describe('guards', () => {
  it('AC-002.1 opens a screen when the user has any of its roles', async () => {
    const { router } = renderApp('/search', { user: 'admin-tr' });
    expect(await heading('Search')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/search');
  });

  it('AC-002.2 shows 403 with MSG08 in place, keeping the URL, when the user has none of the roles', async () => {
    const { router } = renderApp('/wiki/upload', { role: 'EMPLOYEE' });
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/wiki/upload');
  });
});

describe('Workspace Setup', () => {
  it('AC-003.2 sends an Admin to Workspace Setup while the Workspace is not set up', async () => {
    workspaceNotSetUp('admin');
    const { router } = renderApp('/users', { user: 'admin' });
    expect(await heading('Workspace Setup')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/workspace-setup');
  });

  it('AC-003.2 also sends an Admin who opens "/" to Workspace Setup', async () => {
    workspaceNotSetUp('admin-tr');
    const { router } = renderApp('/', { user: 'admin-tr' });
    expect(await heading('Workspace Setup')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/workspace-setup');
  });

  it('AC-003.2 still lets the Admin open My Profile while the Workspace is not set up', async () => {
    workspaceNotSetUp('admin');
    const { router } = renderApp('/profile', { user: 'admin' });
    expect(await heading('My Profile')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/profile');
  });

  it('AC-003.2 treats a trailing slash as the same screen (My Profile stays open)', async () => {
    workspaceNotSetUp('admin');
    const { router } = renderApp('/profile/', { user: 'admin' });
    expect(await heading('My Profile')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/profile/');
  });

  it('AC-003.2 still shows 404 for an unknown path while the Workspace is not set up', async () => {
    workspaceNotSetUp('admin');
    const { router } = renderApp('/no-such-screen', { user: 'admin' });
    expect(
      await screen.findByText('This page does not exist.', {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/no-such-screen');
  });

  it('AC-003.2 does not redirect the dev kit page while the Workspace is not set up', async () => {
    workspaceNotSetUp('admin');
    const { router } = renderApp('/dev/kit', { user: 'admin' });
    expect(await heading('Kit (dev only)')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/dev/kit');
  });

  it('AC-003.3 does not redirect a user without the Admin role while the Workspace is not set up', async () => {
    workspaceNotSetUp('tr');
    const { router } = renderApp('/', { user: 'tr' });
    expect(await heading('Search')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/search');
  });

  it('AC-003.4 does not redirect and shows the session error page when the session cannot be loaded', async () => {
    server.use(http.get('*/api/v1/me', () => problem(500)));
    const { router } = renderApp('/users', { user: 'admin' });
    expect(await screen.findByText(msg('MSG06'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/users');
  });
});

describe('landing page in the app', () => {
  it.each([
    ['admin', '/users', 'User List'],
    ['pm', '/review-queue', 'Review Queue'],
    ['km', '/wiki', 'Wiki Home'],
    ['tr', '/search', 'Search'],
    ['employee', '/chatbot', 'Knowledge Chatbot'],
    ['pm-km', '/review-queue', 'Review Queue'],
  ] as const)('AC-003.1 sends %s from "/" to %s', async (user, path, title) => {
    const { router } = renderApp('/', { user });
    expect(await heading(title)).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe(path));
  });
});
