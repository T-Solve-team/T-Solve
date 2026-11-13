import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';
import { ITSUP_ID } from '../mocks/data';

/** Records the `q` of every candidate request and every add request of this test. */
function recordRequests() {
  const searches: string[] = [];
  const adds: unknown[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.endsWith('/member-candidates')) searches.push(url.searchParams.get('q') ?? '');
    if (url.pathname.endsWith('/members') && request.method === 'POST') adds.push(url.pathname);
  });
  return { searches, adds };
}

async function openPopup() {
  const user = userEvent.setup();
  renderApp(paths.projectDetail(ITSUP_ID), { role: 'PROJECT_MANAGER' });
  await user.click(await screen.findByRole('button', { name: 'Add member' }, { timeout: 5000 }));
  const dialog = await screen.findByRole('dialog', { name: 'Add member' });
  await within(dialog).findByRole('row', { name: /Phạm Quốc Bảo/ });
  return { user, dialog };
}

const addButton = (dialog: HTMLElement) => within(dialog).getByRole('button', { name: 'Add' });

describe('AddMemberModal', () => {
  afterEach(() => server.events.removeAllListeners());

  it('AC-002.10 loads the candidates at once and searches 300 ms after the last keystroke', async () => {
    const requests = recordRequests();
    const { user, dialog } = await openPopup();
    expect(requests.searches).toEqual(['']);
    expect(within(dialog).queryByRole('row', { name: /Trần Thị Lan/ })).not.toBeInTheDocument();
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Search by name or email' }),
      'mai',
    );
    // The 300 ms debounce plus the request: give the slower CI shards more than findBy's default 1 s.
    await waitFor(
      () =>
        expect(
          within(dialog).queryByRole('row', { name: /Phạm Quốc Bảo/ }),
        ).not.toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(within(dialog).getByRole('row', { name: /Ngô Thị Mai/ })).toBeInTheDocument();
    expect(requests.searches).toEqual(['', 'mai']);
  });

  it('AC-002.10 selects more than one candidate', async () => {
    const { user, dialog } = await openPopup();
    await user.click(within(dialog).getByRole('checkbox', { name: 'Select Phạm Quốc Bảo' }));
    await user.click(within(dialog).getByRole('checkbox', { name: 'Select Ngô Thị Mai' }));
    expect(within(dialog).getByRole('checkbox', { name: 'Select Phạm Quốc Bảo' })).toBeChecked();
    expect(within(dialog).getByRole('checkbox', { name: 'Select Ngô Thị Mai' })).toBeChecked();
  });

  it('AC-002.11 shows MSG04 in the popup when there are no candidates', async () => {
    const { user, dialog } = await openPopup();
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Search by name or email' }),
      'zzz',
    );
    expect(
      await within(dialog).findByText(msg('MSG04'), {}, { timeout: 3000 }),
    ).toBeInTheDocument();
  });

  it('AC-002.12 disables Add while no candidate is selected and while the add is running', async () => {
    let release = () => {};
    server.use(
      http.post(
        '*/api/v1/projects/:projectId/members',
        () =>
          new Promise<Response>((resolve) => {
            release = () => resolve(problem(500));
          }),
      ),
    );
    const { user, dialog } = await openPopup();
    expect(addButton(dialog)).toBeDisabled();
    await user.click(within(dialog).getByRole('checkbox', { name: 'Select Phạm Quốc Bảo' }));
    expect(addButton(dialog)).toBeEnabled();
    await user.click(addButton(dialog));
    await waitFor(() => expect(addButton(dialog)).toBeDisabled());
    release();
    await waitFor(() => expect(addButton(dialog)).toBeEnabled());
  });

  it('AC-002.13 closes the popup, shows MSG05 and the returned member list after a 201', async () => {
    const { user, dialog } = await openPopup();
    await user.click(within(dialog).getByRole('checkbox', { name: 'Select Phạm Quốc Bảo' }));
    await user.click(within(dialog).getByRole('checkbox', { name: 'Select Ngô Thị Mai' }));
    await user.click(addButton(dialog));
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Add member' })).not.toBeInTheDocument(),
    );
    const table = screen.getByRole('table', { name: 'Members' });
    expect(within(table).getByRole('row', { name: /Phạm Quốc Bảo/ })).toBeInTheDocument();
    expect(within(table).getByRole('row', { name: /Ngô Thị Mai/ })).toBeInTheDocument();
  });

  it('AC-002.14 keeps the popup open with the selection, shows MSG06 and reloads the candidates on 409', async () => {
    server.use(http.post('*/api/v1/projects/:projectId/members', () => problem(409, 'MSG06')));
    const { user, dialog } = await openPopup();
    const requests = recordRequests();
    await user.click(within(dialog).getByRole('checkbox', { name: 'Select Phạm Quốc Bảo' }));
    await user.click(addButton(dialog));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    await waitFor(() => expect(requests.searches).toEqual(['']));
    expect(screen.getByRole('dialog', { name: 'Add member' })).toBeInTheDocument();
    expect(within(dialog).getByRole('checkbox', { name: 'Select Phạm Quốc Bảo' })).toBeChecked();
  });

  it('AC-002.15 sends no add request and changes nothing on Cancel or close', async () => {
    const requests = recordRequests();
    const { user, dialog } = await openPopup();
    await user.click(within(dialog).getByRole('checkbox', { name: 'Select Phạm Quốc Bảo' }));
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Add member' })).not.toBeInTheDocument(),
    );
    await user.click(screen.getByRole('button', { name: 'Add member' }));
    const reopened = await screen.findByRole('dialog', { name: 'Add member' });
    await within(reopened).findByRole('row', { name: /Phạm Quốc Bảo/ });
    await user.click(within(reopened).getByRole('checkbox', { name: 'Select Phạm Quốc Bảo' }));
    await user.click(within(reopened).getByRole('button', { name: 'Close' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Add member' })).not.toBeInTheDocument(),
    );
    expect(requests.adds).toEqual([]);
    const table = screen.getByRole('table', { name: 'Members' });
    expect(within(table).queryByRole('row', { name: /Phạm Quốc Bảo/ })).not.toBeInTheDocument();
  });
});
