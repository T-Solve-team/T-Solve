import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import type { CurrentUser } from '@/shared/api';
import { msg } from '@/shared/messages';
import { SESSION_QUERY_KEY, type DevUserId } from '@/shared/auth';
import { renderApp } from '@/test/render';

/** Makes `/me` answer a fixture user with changes (bad or edge data) instead of the dev user's own record. */
function meAs(base: DevUserId, overrides: Partial<CurrentUser>) {
  server.use(
    http.get('*/api/v1/me', () => HttpResponse.json({ ...MOCK_USERS[base], ...overrides })),
  );
}

/** Counts `POST /auth/logout`; `delayMs` keeps the request open so a second click can arrive. */
function signOutSpy(options: { delayMs?: number; fail?: boolean } = {}) {
  const spy = { calls: 0 };
  server.use(
    http.post('*/api/v1/auth/logout', async () => {
      spy.calls += 1;
      if (options.delayMs) await delay(options.delayMs);
      return options.fail ? problem(500) : new HttpResponse(null, { status: 204 });
    }),
  );
  return spy;
}

/** The Account card: a region named "Account" (the developer adds the accessible name). */
async function findAccountCard() {
  return within(await screen.findByRole('region', { name: 'Account' }, { timeout: 5000 }));
}

/** The Spaces card: a region named "Spaces" (the developer adds the accessible name, Q2). */
async function findSpacesCard() {
  return within(await screen.findByRole('region', { name: 'Spaces' }, { timeout: 5000 }));
}

/** The Spaces card once its spinner is gone, i.e. after the list, MSG142 or MSG06 has taken its place. */
async function findLoadedSpacesCard() {
  const region = await screen.findByRole('region', { name: 'Spaces' }, { timeout: 5000 });
  await waitFor(() => expect(within(region).queryByText(/Loading/)).not.toBeInTheDocument(), {
    timeout: 5000,
  });
  return within(region);
}

/** The text of each item of the list with this accessible name. */
function spaceNames(listName: string): string[] {
  return within(screen.getByRole('list', { name: listName }))
    .getAllByRole('listitem')
    .map((item) => item.textContent?.trim() ?? '');
}

const SPACES_URL = /\/api\/v1\/me\/spaces$/;

/** Counts `GET /me/spaces` requests while the real handler keeps answering. Call before `renderApp`. */
function spacesRequests() {
  const spy = { calls: 0 };
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'GET' && SPACES_URL.test(new URL(request.url).pathname)) spy.calls += 1;
  });
  return spy;
}

/** Resolves when the mock has answered `GET /me/spaces`. Call before `renderApp`. */
function spacesAnswered(): Promise<void> {
  return new Promise((resolve) => {
    server.events.on('response:mocked', ({ request }) => {
      if (SPACES_URL.test(new URL(request.url).pathname)) resolve();
    });
  });
}

/** Makes `/me/spaces` answer this list for every user. */
function spacesAre(spaces: { spaceId: string; name: string }[]) {
  server.use(http.get('*/api/v1/me/spaces', () => HttpResponse.json(spaces)));
}

/** The text of a label/value row, without the label and a trailing colon; null when the label is absent. */
function fieldValue(
  card: { queryByText: (text: string, options?: { exact: boolean }) => HTMLElement | null },
  label: string,
): string | null {
  const labelNode = card.queryByText(label, { exact: true });
  if (!labelNode) return null;
  let row: HTMLElement = labelNode;
  while (row.parentElement && row.textContent === labelNode.textContent) row = row.parentElement;
  return (row.textContent ?? '')
    .replace(label, '')
    .replace(/^\s*:?\s*/, '')
    .trim();
}

function projectRows() {
  const table = screen.getByRole('table', { name: 'Projects' });
  return within(table)
    .getAllByRole('row')
    .slice(1)
    .map((row) =>
      within(row)
        .getAllByRole('cell')
        .map((cell) => cell.textContent?.trim()),
    );
}

describe('MyProfilePage', () => {
  beforeAll(async () => {
    await import('./MyProfilePage');
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  describe('AC-003.1 account, Roles, Department, Departments and Projects', () => {
    it('AC-003.1 shows full name, email and Roles "Employee, Ticket Resolver" for tr-two-departments', async () => {
      renderApp('/profile', { user: 'tr-two-departments' });
      const card = await findAccountCard();
      expect(card.getByText('Ngô Thị Mai')).toBeInTheDocument();
      expect(card.getByText('mai.ngo@company.example')).toBeInTheDocument();
      expect(fieldValue(card, 'Roles')).toBe('Employee, Ticket Resolver');
    });

    it('AC-003.1 shows Departments "IT, HR" and no Department row for a Ticket Resolver', async () => {
      renderApp('/profile', { user: 'tr-two-departments' });
      const card = await findAccountCard();
      expect(fieldValue(card, 'Departments')).toBe('IT, HR');
      expect(card.queryByText('Department', { exact: true })).not.toBeInTheDocument();
    });

    it('AC-003.1 lists Projects by Jira key with Department and "Ticket Resolver" role', async () => {
      renderApp('/profile', { user: 'tr-two-departments' });
      await findAccountCard();
      const table = await screen.findByRole('table', { name: 'Projects' });
      for (const name of ['Jira project key', 'Department', 'Role in Project']) {
        expect(within(table).getByRole('columnheader', { name })).toBeInTheDocument();
      }
      expect(within(table).queryByRole('columnheader', { name: /Project ID/ })).toBeNull();
      expect(projectRows()).toEqual([
        ['ITSUP', 'IT', 'Ticket Resolver'],
        ['HRHELP', 'HR', 'Ticket Resolver'],
      ]);
      expect(within(table).queryByText('Member')).not.toBeInTheDocument();
    });

    it('AC-003.1 shows the Department row and "Project Manager" project roles for a PM', async () => {
      renderApp('/profile', { user: 'pm' });
      const card = await findAccountCard();
      expect(fieldValue(card, 'Roles')).toBe('Employee, Project Manager');
      expect(fieldValue(card, 'Department')).toBe('IT');
      await screen.findByRole('table', { name: 'Projects' });
      expect(projectRows()).toEqual([
        ['ITSUP', 'IT', 'Project Manager'],
        ['ITNET', 'IT', 'Project Manager'],
      ]);
    });

    it('AC-003.1 shows Roles in ROLES order with Employee first', async () => {
      renderApp('/profile', { user: 'pm-km' });
      const card = await findAccountCard();
      expect(fieldValue(card, 'Roles')).toBe('Employee, Project Manager, Knowledge Manager');
      expect(fieldValue(card, 'Department')).toBe('HR');
    });
  });

  describe('edge rows for the account and Projects', () => {
    it('edge 53 shows an Employee the Account card and a Spaces card with MSG142, but no Departments or Projects', async () => {
      const answered = spacesAnswered();
      renderApp('/profile', { user: 'employee' });
      const card = await findAccountCard();
      await answered;
      expect(card.getByText('Trần Thị Lan')).toBeInTheDocument();
      expect(card.getByText('lan.tran@company.example')).toBeInTheDocument();
      expect(fieldValue(card, 'Roles')).toBe('Employee');
      expect(card.queryByText('Department', { exact: true })).not.toBeInTheDocument();
      expect(card.queryByText('Departments', { exact: true })).not.toBeInTheDocument();
      expect(screen.queryByRole('table', { name: 'Projects' })).not.toBeInTheDocument();
      expect(screen.queryByText('Projects', { exact: true })).not.toBeInTheDocument();
      const spaces = await findLoadedSpacesCard();
      expect(spaces.getByText(msg('MSG142'))).toBeInTheDocument();
      expect(spaces.queryAllByRole('listitem')).toHaveLength(0);
      expect(screen.queryByText('Spaces I manage')).not.toBeInTheDocument();
    });

    it('edge 54 shows no Department row for a Ticket Resolver', async () => {
      renderApp('/profile', { user: 'tr' });
      const card = await findAccountCard();
      expect(fieldValue(card, 'Departments')).toBe('IT');
      expect(card.queryByText('Department', { exact: true })).not.toBeInTheDocument();
    });

    it('edge 54 hides the Department row for a non-PM whose session carries a department', async () => {
      meAs('tr', { department: MOCK_USERS.pm.department });
      renderApp('/profile', { user: 'tr' });
      const card = await findAccountCard();
      expect(card.getByText('Phạm Quốc Bảo')).toBeInTheDocument();
      expect(card.queryByText('Department', { exact: true })).not.toBeInTheDocument();
    });

    it('edge 55 shows five Roles with Employee first in ROLES order, whatever the order in /me', async () => {
      meAs('admin-tr', {
        roles: ['ADMIN', 'KNOWLEDGE_MANAGER', 'TICKET_RESOLVER', 'PROJECT_MANAGER', 'EMPLOYEE'],
      });
      renderApp('/profile', { user: 'admin-tr' });
      const card = await findAccountCard();
      expect(fieldValue(card, 'Roles')).toBe(
        'Employee, Ticket Resolver, Project Manager, Knowledge Manager, Admin',
      );
    });
  });

  describe('AC-003.2 Spaces I manage', () => {
    it('AC-003.2 lists "Spaces I manage" with the managed Space for a Knowledge Manager', async () => {
      renderApp('/profile', { user: 'km' });
      expect(await screen.findByText('Spaces I manage', {}, { timeout: 5000 })).toBeInTheDocument();
      expect(
        within(screen.getByRole('list', { name: 'Spaces I manage' })).getByText('IT Space'),
      ).toBeInTheDocument();
    });

    it('AC-003.2 lists "Spaces I manage" for a Project Manager who is also a Knowledge Manager', async () => {
      renderApp('/profile', { user: 'pm-km' });
      expect(await screen.findByText('Spaces I manage', {}, { timeout: 5000 })).toBeInTheDocument();
      expect(
        within(screen.getByRole('list', { name: 'Spaces I manage' })).getByText('HR Space'),
      ).toBeInTheDocument();
    });

    it('AC-003.2 does not show "Spaces I manage" for a Project Manager who is not a Knowledge Manager', async () => {
      renderApp('/profile', { user: 'pm' });
      await findAccountCard();
      const spaces = await findLoadedSpacesCard();
      expect(spaces.getByRole('list', { name: 'Spaces I can view' })).toBeInTheDocument();
      expect(spaces.getAllByRole('listitem')).toHaveLength(2);
      expect(screen.queryByText('Spaces I manage')).not.toBeInTheDocument();
    });

    it.each(['tr', 'admin', 'employee'] as const)(
      'AC-003.2 does not show "Spaces I manage" for %s',
      async (user) => {
        renderApp('/profile', { user });
        await findAccountCard();
        expect(screen.queryByText('Spaces I manage')).not.toBeInTheDocument();
      },
    );

    it('edge 56 hides "Spaces I manage" and shows the Spaces card with MSG142 for a Knowledge Manager with no managed and no viewable Spaces', async () => {
      meAs('km', { managedSpaces: [] });
      spacesAre([]);
      const answered = spacesAnswered();
      renderApp('/profile', { user: 'km' });
      const card = await findAccountCard();
      await answered;
      expect(card.getByText('Lê Thu Hương')).toBeInTheDocument();
      expect(screen.queryByText('Spaces I manage')).not.toBeInTheDocument();
      const spaces = await findLoadedSpacesCard();
      expect(spaces.getByText(msg('MSG142'))).toBeInTheDocument();
      expect(spaces.queryAllByRole('listitem')).toHaveLength(0);
    });

    it('edge 74 cuts a long managed Space name to one line with the full name as its title', async () => {
      const name = 'x'.repeat(120);
      meAs('km', { managedSpaces: [{ spaceId: '00000000-0000-4000-8000-0000000000a9', name }] });
      renderApp('/profile', { user: 'km' });
      await screen.findByText('Spaces I manage', {}, { timeout: 5000 });
      const item = screen.getByText(name);
      expect(item.closest('.ant-typography-ellipsis')).not.toBeNull();
      expect(item.closest('[title]')?.getAttribute('title')).toBe(name);
    });
  });

  describe('AC-003.3 Spaces I can view', () => {
    it('AC-003.3 loads /me/spaces once on open and lists "Spaces I can view" sorted by name for tr', async () => {
      const spy = spacesRequests();
      renderApp('/profile', { user: 'tr' });
      await findLoadedSpacesCard();
      const names = spaceNames('Spaces I can view');
      expect(names).toHaveLength(3);
      expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b, 'vi')));
      const list = screen.getByRole('list', { name: 'Spaces I can view' });
      list.querySelectorAll('.anticon').forEach((icon) => {
        expect(icon).toHaveAttribute('aria-hidden', 'true');
      });
      expect(list.querySelectorAll('.anticon-folder-open, .anticon-folder')).toHaveLength(3);
      expect(screen.queryByRole('list', { name: 'Spaces I manage' })).not.toBeInTheDocument();
      expect(spy.calls).toBe(1);
    });

    it('AC-003.3 shows the Spaces card with a spinner while /me/spaces loads', async () => {
      server.use(http.get('*/api/v1/me/spaces', () => delay('infinite')));
      renderApp('/profile', { user: 'employee' });
      const card = await findSpacesCard();
      expect(card.getByText(/Loading/)).toBeInTheDocument();
      expect(screen.queryByRole('list', { name: 'Spaces I can view' })).not.toBeInTheDocument();
      expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    });

    it('AC-003.15 shows the Spaces card with a spinner while the viewable Spaces load', async () => {
      server.use(
        http.get('*/api/v1/me/spaces', async () => {
          await delay(400);
          return HttpResponse.json([
            { spaceId: '00000000-0000-4000-8000-0000000000b1', name: 'Late Space' },
          ]);
        }),
      );
      renderApp('/profile', { user: 'tr' });
      await findAccountCard();
      const region = screen.getByRole('region', { name: 'Spaces' });
      expect(within(region).getByText(/Loading/)).toBeInTheDocument();
      expect(within(region).queryByRole('list', { name: 'Spaces I can view' })).toBeNull();
      const list = await within(region).findByRole('list', { name: 'Spaces I can view' });
      expect(within(list).getByText('Late Space')).toBeInTheDocument();
      expect(within(region).queryByText(/Loading/)).not.toBeInTheDocument();
    });

    it.each(Object.keys(MOCK_USERS) as DevUserId[])(
      'AC-003.15 renders the Spaces card for %s',
      async (user) => {
        renderApp('/profile', { user });
        await findAccountCard();
        const spaces = await findLoadedSpacesCard();
        expect(spaces.getByText('Spaces I can view')).toBeInTheDocument();
      },
    );

    it('AC-003.15 shows MSG142 "No Spaces found" under "Spaces I can view" for employee', async () => {
      renderApp('/profile', { user: 'employee' });
      await findAccountCard();
      const spaces = await findLoadedSpacesCard();
      expect(spaces.getByText('Spaces I can view')).toBeInTheDocument();
      expect(spaces.getByText(msg('MSG142'))).toBeInTheDocument();
      expect(spaces.queryAllByRole('listitem')).toHaveLength(0);
      expect(screen.queryByRole('list', { name: 'Spaces I can view' })).not.toBeInTheDocument();
      expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    });

    it('edge 76 shows km\'s "IT Space" in both "Spaces I manage" and "Spaces I can view", managed list first', async () => {
      renderApp('/profile', { user: 'km' });
      await findLoadedSpacesCard();
      const managed = screen.getByRole('list', { name: 'Spaces I manage' });
      const viewable = screen.getByRole('list', { name: 'Spaces I can view' });
      expect(within(managed).getByText('IT Space')).toBeInTheDocument();
      expect(within(viewable).getByText('IT Space')).toBeInTheDocument();
      expect(spaceNames('Spaces I can view')).toHaveLength(3);
      expect(managed.compareDocumentPosition(viewable) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
        Node.DOCUMENT_POSITION_FOLLOWING,
      );
    });

    it('edge 79 shows MSG142 under "Spaces I can view" when /me/spaces answers [] and keeps "Spaces I manage" for km', async () => {
      spacesAre([]);
      const answered = spacesAnswered();
      renderApp('/profile', { user: 'km' });
      await findSpacesCard();
      await answered;
      const card = await findLoadedSpacesCard();
      expect(
        within(screen.getByRole('list', { name: 'Spaces I manage' })).getByText('IT Space'),
      ).toBeInTheDocument();
      expect(card.getByText('Spaces I can view')).toBeInTheDocument();
      expect(card.getByText(msg('MSG142'))).toBeInTheDocument();
      expect(screen.queryByRole('list', { name: 'Spaces I can view' })).not.toBeInTheDocument();
      expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    });

    it('AC-003.14 spaces shows MSG06 in the Spaces card when /me/spaces fails and still shows the account', async () => {
      server.use(http.get('*/api/v1/me/spaces', () => problem(500)));
      renderApp('/profile', { user: 'employee' });
      const card = await findSpacesCard();
      expect(await card.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
      const account = await findAccountCard();
      expect(account.getByText('Trần Thị Lan')).toBeInTheDocument();
      expect(account.getByText('lan.tran@company.example')).toBeInTheDocument();
      expect(account.getByText('Roles')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Try again/ })).not.toBeInTheDocument();
      expect(screen.queryByRole('list', { name: 'Spaces I can view' })).not.toBeInTheDocument();
    });

    it('AC-003.14 spaces keeps "Spaces I manage" and shows MSG06 in place of "Spaces I can view" for km (edge 66)', async () => {
      server.use(http.get('*/api/v1/me/spaces', () => problem(500)));
      renderApp('/profile', { user: 'km' });
      const card = await findSpacesCard();
      expect(await card.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
      expect(
        within(screen.getByRole('list', { name: 'Spaces I manage' })).getByText('IT Space'),
      ).toBeInTheDocument();
      await findAccountCard();
      expect(screen.getAllByText(msg('MSG06'))).toHaveLength(1);
    });

    it('edge 67 a 401 on /me/spaces opens the MSG07 dialog and shows no MSG06 in the Spaces card', async () => {
      server.use(http.get('*/api/v1/me/spaces', () => problem(401)));
      renderApp('/profile', { user: 'tr' });
      expect(await screen.findByRole('dialog', {}, { timeout: 5000 })).toHaveTextContent(
        msg('MSG07'),
      );
      expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
    });

    it("edge 58 cuts admin-tr's 120-character viewable Space name to one line with the full name on hover", async () => {
      renderApp('/profile', { user: 'admin-tr' });
      await findLoadedSpacesCard();
      const names = spaceNames('Spaces I can view');
      expect(names).toHaveLength(6);
      const long = names.find((name) => name.length === 120);
      expect(long).toBeDefined();
      const item = screen.getByText(long ?? '');
      expect(item.closest('.ant-typography-ellipsis')).not.toBeNull();
      expect(item.closest('[title]')?.getAttribute('title')).toBe(long);
    });

    it('edge 62 lists 40 viewable Spaces in a plain list without paging', async () => {
      const spaces = Array.from({ length: 40 }, (_, index) => ({
        spaceId: `00000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
        name: `Space ${String(index + 1).padStart(2, '0')}`,
      }));
      spacesAre(spaces);
      const { container } = renderApp('/profile', { user: 'tr' });
      await findLoadedSpacesCard();
      expect(spaceNames('Spaces I can view')).toHaveLength(40);
      expect(container.querySelector('.ant-pagination')).toBeNull();
      expect(screen.queryByRole('button', { name: /Load more|Next/ })).not.toBeInTheDocument();
    });
  });

  describe('AC-002.7 Log out', () => {
    it('AC-002.7 shows a "Log out" button in the Account card', async () => {
      renderApp('/profile', { user: 'tr' });
      const card = await findAccountCard();
      expect(card.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
    });

    it('AC-002.7 sends one logout request and opens Login', async () => {
      const user = userEvent.setup();
      const spy = signOutSpy();
      const { router } = renderApp('/profile', { user: 'km' });
      const card = await findAccountCard();
      await user.click(card.getByRole('button', { name: 'Log out' }));
      await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
      expect(spy.calls).toBe(1);
    });

    it('AC-002.7 a double click sends one logout request while the button is loading', async () => {
      const user = userEvent.setup();
      const spy = signOutSpy({ delayMs: 150 });
      const { router } = renderApp('/profile', { user: 'km' });
      const card = await findAccountCard();
      await user.dblClick(card.getByRole('button', { name: 'Log out' }));
      await waitFor(() =>
        expect(
          within(screen.getByRole('region', { name: 'Account' })).getByRole('button', {
            name: /Log out/,
          }),
        ).toHaveClass('ant-btn-loading'),
      );
      await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
      expect(spy.calls).toBe(1);
    });

    it('AC-002.7 still opens Login when logout fails (fe-foundation AC-008.3)', async () => {
      const user = userEvent.setup();
      const spy = signOutSpy({ fail: true });
      const { router } = renderApp('/profile', { user: 'km' });
      const card = await findAccountCard();
      await user.click(card.getByRole('button', { name: 'Log out' }));
      await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
      expect(spy.calls).toBe(1);
    });

    it('edge 57 a double click on Log out sends one request and leaves no profile data behind', async () => {
      const user = userEvent.setup();
      const spy = signOutSpy({ delayMs: 100 });
      const { router, queryClient } = renderApp('/profile', { user: 'pm-km' });
      const card = await findAccountCard();
      await user.dblClick(card.getByRole('button', { name: 'Log out' }));
      await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
      expect(spy.calls).toBe(1);
      expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull();
      expect(screen.queryByText('Võ Thanh Tâm')).not.toBeInTheDocument();
      expect(screen.queryByText('tam.vo@company.example')).not.toBeInTheDocument();
    });

    it('edge 80 after Log out no profile data is left in the cache or the document, and Back shows no profile', async () => {
      const user = userEvent.setup();
      signOutSpy();
      const { router, queryClient } = renderApp('/profile', { user: 'pm-km' });
      const card = await findAccountCard();
      await user.click(card.getByRole('button', { name: 'Log out' }));
      await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
      const leftovers = queryClient
        .getQueryCache()
        .getAll()
        .filter((query) => query.queryKey[0] !== SESSION_QUERY_KEY[0]);
      expect(leftovers).toHaveLength(0);
      expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull();
      expect(screen.queryByText('tam.vo@company.example')).not.toBeInTheDocument();
      expect(screen.queryByText('HR Space')).not.toBeInTheDocument();
      await router.navigate(-1);
      expect(router.state.location.pathname).toBe('/login');
      expect(screen.queryByText('tam.vo@company.example')).not.toBeInTheDocument();
    });

    it('edge 72 the Log out button is reachable with Tab and runs with Enter', async () => {
      const user = userEvent.setup();
      signOutSpy();
      const { router } = renderApp('/profile', { user: 'km' });
      const card = await findAccountCard();
      const button = card.getByRole('button', { name: 'Log out' });
      for (let i = 0; i < 40 && document.activeElement !== button; i += 1) await user.tab();
      expect(button).toHaveFocus();
      await user.keyboard('{Enter}');
      await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    });
  });

  describe('accessibility and privacy edge rows', () => {
    it('edge 72 names the Account card and the Projects table, and hides the folder icons from assistive technology', async () => {
      const { container } = renderApp('/profile', { user: 'pm-km' });
      const card = await findAccountCard();
      expect(card.getByText('Account')).toBeVisible();
      expect(await screen.findByRole('table', { name: 'Projects' })).toBeInTheDocument();
      await screen.findByText('Spaces I manage');
      const icons = container.querySelectorAll('.anticon-folder-open, .anticon-folder');
      expect(icons.length).toBeGreaterThan(0);
      icons.forEach((icon) => expect(icon).toHaveAttribute('aria-hidden', 'true'));
    });

    it('edge 81 keeps the URL and the browser storage free of personal data', async () => {
      const { router } = renderApp('/profile', { user: 'tr' });
      await findAccountCard();
      expect(router.state.location.pathname).toBe('/profile');
      expect(router.state.location.search).toBe('');
      for (const storage of [window.localStorage, window.sessionStorage]) {
        for (let i = 0; i < storage.length; i += 1) {
          const key = storage.key(i) ?? '';
          const stored = `${key} ${storage.getItem(key) ?? ''}`;
          expect(stored).not.toMatch(/profile|bao\.pham@company\.example|Phạm Quốc Bảo/i);
        }
      }
    });
  });
});
