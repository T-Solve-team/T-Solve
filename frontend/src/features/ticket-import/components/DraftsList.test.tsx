import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { MOCK_USERS } from '@/mocks/session';
import { problem } from '@/shared/api/mocks';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

/** Import Tickets as the mock Project Manager; resolves once the seed Draft (ITSUP, 3 rows) is listed. */
const SAVED = formatDateTime('2026-10-16T02:30:00Z');
const OPEN = `Open Draft ITSUP · IT, saved ${SAVED}`;
const DELETE = `Delete Draft ITSUP · IT, saved ${SAVED}`;

async function openDrafts() {
  const user = userEvent.setup();
  const view = renderApp('/import', { role: 'PROJECT_MANAGER' });
  const table = await screen.findByRole('table', { name: 'Drafts' }, { timeout: 5000 });
  await within(table).findByText('ITSUP · IT');
  return { user, table, ...view };
}

describe('DraftsList', () => {
  beforeAll(async () => {
    await import('../pages/ImportTicketsPage');
  });

  it('AC-004.2 lists the Drafts with type, Project, rows and date saved', async () => {
    const { table } = await openDrafts();

    const row = within(table).getByText('ITSUP · IT').closest('tr');
    if (!(row instanceof HTMLElement)) throw new Error('no Draft row');
    expect(within(row).getByText('Jira')).toBeInTheDocument();
    expect(within(row).getByText('3')).toBeInTheDocument();
    expect(within(row).getByText(formatDateTime('2026-10-16T02:30:00Z'))).toBeInTheDocument();
  });

  it('AC-004.2 shows MSG04 when the user has no Draft', async () => {
    server.use(http.get('*/api/v1/imports/drafts', () => Response.json([])));
    renderApp('/import', { role: 'PROJECT_MANAGER' });

    const table = await screen.findByRole('table', { name: 'Drafts' }, { timeout: 5000 });
    expect(await within(table).findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('names each Open and Delete button after its Draft (BUG-194)', async () => {
    const { table } = await openDrafts();

    expect(within(table).getByRole('button', { name: OPEN })).toBeInTheDocument();
    expect(within(table).getByRole('button', { name: DELETE })).toBeInTheDocument();
  });

  it('AC-004.2 hides a Draft whose Project the user no longer manages (BUG-195)', async () => {
    const unmanaged = '00000000-0000-4000-8000-000000000999';
    server.use(
      http.get('*/api/v1/imports/drafts', () =>
        Response.json([
          {
            previewId: 900,
            source: 'JIRA_IMPORT',
            projectId: '00000000-0000-4000-8000-000000000101',
            rowCount: 3,
            savedAt: '2026-10-16T02:30:00Z',
          },
          {
            previewId: 901,
            source: 'JIRA_IMPORT',
            projectId: unmanaged,
            rowCount: 2,
            savedAt: '2026-10-17T02:30:00Z',
          },
        ]),
      ),
    );
    const { table } = await openDrafts();

    expect(within(table).queryByText(unmanaged)).not.toBeInTheDocument();
    expect(within(table).getAllByRole('button', { name: /^Open Draft/ })).toHaveLength(1);
  });

  it('AC-004.2 keeps a Draft of a REMOVED Project the user still manages (BUG-217)', async () => {
    const pm = MOCK_USERS.pm;
    const removed = {
      projectId: '00000000-0000-4000-8000-000000000301',
      jiraProjectKey: 'ITOLD',
      departmentId: '00000000-0000-4000-8000-0000000000d1',
      departmentName: 'IT',
      roleInProject: 'PROJECT_MANAGER' as const,
      projectStatus: 'REMOVED' as const,
    };
    server.use(
      http.get('*/api/v1/me', () => Response.json({ ...pm, projects: [...pm.projects, removed] })),
      http.get('*/api/v1/imports/drafts', () =>
        Response.json([
          {
            previewId: 900,
            source: 'JIRA_IMPORT',
            projectId: '00000000-0000-4000-8000-000000000101',
            rowCount: 3,
            savedAt: '2026-10-16T02:30:00Z',
          },
          {
            previewId: 902,
            source: 'MANUAL',
            projectId: removed.projectId,
            rowCount: 1,
            savedAt: '2026-10-17T02:30:00Z',
          },
        ]),
      ),
    );
    const { table } = await openDrafts();

    expect(within(table).getByText('ITOLD · IT')).toBeInTheDocument();
    expect(within(table).getAllByRole('button', { name: /^Delete Draft/ })).toHaveLength(2);
  });

  it('AC-007.3 lists a Manual Draft with type Manual and 1 row beside the other Drafts', async () => {
    server.use(
      http.get('*/api/v1/imports/drafts', () =>
        Response.json([
          {
            previewId: 950,
            source: 'MANUAL',
            projectId: '00000000-0000-4000-8000-000000000101',
            rowCount: 1,
            savedAt: '2026-11-10T02:30:00Z',
          },
          {
            previewId: 900,
            source: 'JIRA_IMPORT',
            projectId: '00000000-0000-4000-8000-000000000101',
            rowCount: 3,
            savedAt: '2026-10-16T02:30:00Z',
          },
        ]),
      ),
    );
    renderApp('/import', { role: 'PROJECT_MANAGER' });
    const table = await screen.findByRole('table', { name: 'Drafts' }, { timeout: 5000 });

    const manual = (await within(table).findByText('Manual')).closest('tr');
    if (!(manual instanceof HTMLElement)) throw new Error('no Manual Draft row');
    expect(within(manual).getByText('ITSUP · IT')).toBeInTheDocument();
    expect(within(manual).getByText('1')).toBeInTheDocument();
    expect(within(manual).getByText(formatDateTime('2026-11-10T02:30:00Z'))).toBeInTheDocument();
    expect(within(table).getByText('Jira')).toBeInTheDocument();
  });

  it('AC-004.3 Open goes to the Import Preview of the Draft', async () => {
    const { user, table, router } = await openDrafts();

    await user.click(within(table).getByRole('button', { name: OPEN }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/import/preview'));
    expect(router.state.location.search).toBe('?previewId=900');
  });

  it('AC-004.4 Delete asks MSG42 and removes the Draft once confirmed', async () => {
    const { user, table } = await openDrafts();

    await user.click(within(table).getByRole('button', { name: DELETE }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG42'));
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await within(table).findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-004.4 Cancel keeps the Draft', async () => {
    const { user, table } = await openDrafts();

    await user.click(within(table).getByRole('button', { name: DELETE }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }),
    );

    expect(within(table).getByText('ITSUP · IT')).toBeInTheDocument();
  });

  it('AC-004.5 shows MSG04 when the Draft is already gone', async () => {
    server.use(http.delete('*/api/v1/imports/drafts/:previewId', () => problem(404, 'MSG04')));
    const { user, table } = await openDrafts();

    await user.click(within(table).getByRole('button', { name: DELETE }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }),
    );

    expect(await screen.findAllByText(msg('MSG04'))).not.toHaveLength(0);
  });
});
