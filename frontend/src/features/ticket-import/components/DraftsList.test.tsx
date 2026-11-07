import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

/** Import Tickets as the mock Project Manager; resolves once the seed Draft (ITSUP, 3 rows) is listed. */
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

  it('AC-004.3 Open goes to the Import Preview of the Draft', async () => {
    const { user, table, router } = await openDrafts();

    await user.click(within(table).getByRole('button', { name: 'Open' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/import/preview'));
    expect(router.state.location.search).toBe('?previewId=900');
  });

  it('AC-004.4 Delete asks MSG42 and removes the Draft once confirmed', async () => {
    const { user, table } = await openDrafts();

    await user.click(within(table).getByRole('button', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG42'));
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await within(table).findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-004.4 Cancel keeps the Draft', async () => {
    const { user, table } = await openDrafts();

    await user.click(within(table).getByRole('button', { name: 'Delete' }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }),
    );

    expect(within(table).getByText('ITSUP · IT')).toBeInTheDocument();
  });

  it('AC-004.5 shows MSG04 when the Draft is already gone', async () => {
    server.use(http.delete('*/api/v1/imports/drafts/:previewId', () => problem(404, 'MSG04')));
    const { user, table } = await openDrafts();

    await user.click(within(table).getByRole('button', { name: 'Delete' }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }),
    );

    expect(await screen.findAllByText(msg('MSG04'))).not.toHaveLength(0);
  });
});
