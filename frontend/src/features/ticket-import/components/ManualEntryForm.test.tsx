import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { ManualTicketDraft, ManualTicketRequest } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const ITSUP_ID = '00000000-0000-4000-8000-000000000101';

type User = ReturnType<typeof userEvent.setup>;

/** Import Tickets on the Manual entry tab, as the mock Project Manager. */
async function openManualTab(path = '/import?tab=manual') {
  const user = userEvent.setup();
  const view = renderApp(path, { role: 'PROJECT_MANAGER' });
  await screen.findByRole('button', { name: 'Save as draft' }, { timeout: 5000 });
  return { user, ...view };
}

async function chooseProject(user: User) {
  await user.click(screen.getByRole('combobox', { name: 'Project' }));
  await user.click(await screen.findByTitle('ITSUP · IT'));
}

/** The Project select, which shows the chosen Project. */
function chosenProject() {
  return screen.getByRole('combobox', { name: 'Project' }).closest('.ant-select');
}

const FIELDS = {
  'Ticket ID': 'ITSUP-2001',
  Title: 'VPN drops every hour',
  Description: 'The VPN client disconnects every hour.',
  Symptoms: 'The VPN disconnects.',
  'Root cause': 'An old client version.',
  'Resolution steps': 'Update the client.',
  Scope: 'Windows laptops.',
};

async function fillAll(user: User, values: Partial<typeof FIELDS> = FIELDS) {
  await chooseProject(user);
  for (const [label, value] of Object.entries(values)) {
    await user.type(screen.getByLabelText(label), value);
  }
}

/** The bodies of the requests to `path` that reach the mock backend. */
function captureBodies(path: string) {
  const bodies: ManualTicketRequest[] = [];
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === path) {
      void request
        .clone()
        .json()
        .then((body: ManualTicketRequest) => bodies.push(body));
    }
  });
  return bodies;
}

describe('ManualEntryForm', () => {
  beforeAll(async () => {
    await import('../pages/ImportTicketsPage');
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('AC-006.9 Save as draft shows MSG41, lists the Draft and keeps its id for the next save', async () => {
    const drafts = captureBodies('/api/v1/imports/manual/drafts');
    const { user } = await openManualTab();
    await chooseProject(user);
    await user.type(screen.getByLabelText('Title'), 'Half written');

    await user.click(screen.getByRole('button', { name: 'Save as draft' }));

    expect(await screen.findByText(msg('MSG41'))).toBeInTheDocument();
    const table = screen.getByRole('table', { name: 'Drafts' });
    expect(await within(table).findByText('Manual')).toBeInTheDocument();
    expect(screen.getByLabelText('Title')).toHaveValue('Half written');

    // the loading icon's leave motion never ends in jsdom: it stays in the button's name
    await user.click(screen.getByRole('button', { name: /Save as draft$/ }));
    await waitFor(() => expect(drafts).toHaveLength(2));
    expect(drafts[0]).toMatchObject({ projectId: ITSUP_ID, title: 'Half written', draftId: null });
    expect(drafts[1]?.draftId).toEqual(expect.any(Number));
  });

  it('AC-006.9 Save as draft without a Project shows MSG01 under Project and sends nothing', async () => {
    const drafts = captureBodies('/api/v1/imports/manual/drafts');
    const { user } = await openManualTab();
    await user.type(screen.getByLabelText('Title'), 'No Project');

    await user.click(screen.getByRole('button', { name: 'Save as draft' }));

    expect(await screen.findByText(msg('MSG01', { field_name: 'Project' }))).toBeInTheDocument();
    expect(drafts).toHaveLength(0);
  });

  it('AC-006.11 a reopened Draft fills every field', async () => {
    const draft: ManualTicketDraft = {
      previewId: 801,
      projectId: ITSUP_ID,
      ticketId: 'ITSUP-2002',
      title: 'Printer offline',
      description: 'The printer shows offline.',
      resolvedDate: '2026-09-28',
      symptoms: 'Offline.',
      rootCause: 'Driver.',
      resolutionSteps: 'Reinstall.',
      workaround: 'Print to PDF.',
      scope: 'Floor 3.',
      savedAt: '2026-10-16T02:30:00Z',
    };
    server.use(http.get('*/api/v1/imports/manual/drafts/801', () => HttpResponse.json(draft)));

    await openManualTab('/import?tab=manual&draftId=801');

    expect(screen.getByRole('tab', { name: 'Manual entry' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(chosenProject()).toHaveTextContent('ITSUP · IT');
    expect(screen.getByLabelText('Ticket ID')).toHaveValue('ITSUP-2002');
    expect(screen.getByLabelText('Title')).toHaveValue('Printer offline');
    expect(screen.getByLabelText('Description')).toHaveValue('The printer shows offline.');
    expect(screen.getByLabelText('Resolved date')).toHaveValue('28/09/2026');
    expect(screen.getByLabelText('Symptoms')).toHaveValue('Offline.');
    expect(screen.getByLabelText('Root cause')).toHaveValue('Driver.');
    expect(screen.getByLabelText('Resolution steps')).toHaveValue('Reinstall.');
    expect(screen.getByLabelText('Workaround')).toHaveValue('Print to PDF.');
    expect(screen.getByLabelText('Scope')).toHaveValue('Floor 3.');
  });

  it('AC-006.11 Open on a Manual Draft goes to the Manual entry tab with its id', async () => {
    const { user, router } = await openManualTab();
    await chooseProject(user);
    await user.click(screen.getByRole('button', { name: 'Save as draft' }));
    const table = screen.getByRole('table', { name: 'Drafts' });
    const row = (await within(table).findByText('Manual')).closest('tr');
    if (!(row instanceof HTMLElement)) throw new Error('no Draft row');

    await user.click(within(row).getByRole('button', { name: /^Open Draft/ }));

    await waitFor(() => expect(router.state.location.search).toMatch(/draftId=\d+/));
    expect(router.state.location.pathname).toBe('/import');
    expect(router.state.location.search).toMatch(/^\?tab=manual&draftId=\d+$/);
  });

  it('AC-006.14 shows each field error and keeps the input', async () => {
    server.use(
      http.post('*/api/v1/imports/manual', () =>
        problem(400, 'MSG02', {
          fieldErrors: [
            { field: 'title', code: 'MSG02', params: { field_name: 'Title', max_length: 200 } },
            { field: 'resolvedDate', code: 'MSG39' },
            {
              field: 'solution',
              code: 'MSG02',
              params: { field_name: 'Solution', max_length: 10000 },
            },
          ],
        }),
      ),
    );
    const { user } = await openManualTab();
    await fillAll(user);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByText(msg('MSG02', { field_name: 'Title', max_length: 200 })),
    ).toBeInTheDocument();
    expect(screen.getByText(msg('MSG39'))).toBeInTheDocument();
    expect(
      screen.getByText(msg('MSG02', { field_name: 'Solution', max_length: 10000 })),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Title')).toHaveValue(FIELDS.Title);
    expect(screen.getByLabelText('Scope')).toHaveValue(FIELDS.Scope);
    expect(chosenProject()).toHaveTextContent('ITSUP · IT');
  });

  it('AC-006.14 shows MSG31 under Ticket ID when the Ticket ID is taken', async () => {
    const { user } = await openManualTab();
    await fillAll(user, { ...FIELDS, 'Ticket ID': 'ITSUP-0871' });

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(msg('MSG31', { ticket_id: 'ITSUP-0871' }))).toBeInTheDocument();
    expect(screen.getByLabelText('Ticket ID')).toHaveValue('ITSUP-0871');
  });

  it('AC-006.14 shows the message above the form for a rejection without field errors', async () => {
    server.use(http.post('*/api/v1/imports/manual', () => problem(403, 'MSG08')));
    const { user } = await openManualTab();
    await fillAll(user);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG08'));
    expect(screen.getByLabelText('Title')).toHaveValue(FIELDS.Title);
  });

  it('AC-006.14 a successful save shows MSG32 and clears the form', async () => {
    const saves = captureBodies('/api/v1/imports/manual');
    const { user } = await openManualTab();
    await fillAll(user);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(msg('MSG32', { created: 1, skipped: 0 }))).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Title')).toHaveValue(''));
    expect(screen.getByLabelText('Ticket ID')).toHaveValue('');
    expect(saves[0]).toMatchObject({ projectId: ITSUP_ID, ticketId: 'ITSUP-2001', draftId: null });
  });
});
