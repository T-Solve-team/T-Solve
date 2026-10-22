import { screen, waitFor } from '@testing-library/react';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

describe('routing and access', () => {
  it.each([
    ['ADMIN', '/users', 'User List'],
    ['DEPARTMENT_MANAGER', '/dashboard', 'Knowledge Dashboard'],
    ['PROJECT_MANAGER', '/review-queue', 'Review Queue'],
    ['STAFF', '/search', 'Search'],
  ] as const)('sends %s from "/" to their landing page (BUG-017)', async (role, path, title) => {
    const { router } = renderApp('/', { role });
    // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
    expect(
      await screen.findByRole('heading', { level: 1, name: title }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(path);
  });

  it('shows 403 with MSG08 when the role may not open the screen, keeping the URL', async () => {
    const { router } = renderApp('/review-queue', { role: 'STAFF' });
    expect(await screen.findByText(msg('MSG08'))).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/review-queue');
  });

  it('sends a signed-out user to Login with a returnTo link', async () => {
    const { router } = renderApp('/tickets/42', { role: null });
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.search).toBe(`?returnTo=${encodeURIComponent('/tickets/42')}`);
  });

  it('sends a signed-in user away from Login', async () => {
    const { router } = renderApp('/login', { role: 'STAFF' });
    await waitFor(() => expect(router.state.location.pathname).toBe('/search'));
  });

  it('shows 404 for an unknown path', async () => {
    renderApp('/no-such-screen');
    expect(await screen.findByText('This page does not exist.')).toBeInTheDocument();
  });

  it("shows the role's menu and highlights the list of a detail screen", async () => {
    renderApp('/tickets/42', { role: 'DEPARTMENT_MANAGER' });
    const menu = await screen.findByRole('menu', { name: 'Main menu' });
    const items = await waitFor(() => {
      const found = menu.querySelectorAll('[role="menuitem"]');
      expect(found).toHaveLength(4);
      return found;
    });
    expect([...items].map((item) => item.textContent)).toEqual([
      'Knowledge Dashboard',
      'Search',
      'Ticket List',
      'Jira Integration',
    ]);
    expect(menu.querySelector('.ant-menu-item-selected')?.textContent).toBe('Ticket List');
  });
});
