import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';
import { ITSUP_ID } from '../mocks/data';
import { JIRA_TAB_TEXTS } from './jiraTabTexts';

const BAO = '00000000-0000-4000-8000-000000000301';
const MAI = '00000000-0000-4000-8000-000000000302';

async function openPopup() {
  const user = userEvent.setup();
  renderApp(paths.projectDetail(ITSUP_ID), { role: 'PROJECT_MANAGER' });
  await user.click(await screen.findByRole('button', { name: 'Add member' }, { timeout: 5000 }));
  const dialog = await screen.findByRole('dialog', { name: 'Add member' });
  await within(dialog).findByRole('row', { name: /Bảo Phạm/ });
  return { user, dialog };
}

/** Records the body of every add request of this test. */
function recordAdds() {
  const adds: unknown[] = [];
  server.events.on('request:start', async ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.endsWith('/members') && request.method === 'POST') {
      adds.push(await request.clone().json());
    }
  });
  return adds;
}

describe('AddMemberModal — From Jira', () => {
  afterEach(() => server.events.removeAllListeners());

  it('AC-011.6 opens on the From Jira tab, next to the Search tab', async () => {
    const { dialog } = await openPopup();

    expect(within(dialog).getByRole('tab', { name: 'From Jira' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(within(dialog).getByRole('tab', { name: 'Search' })).toBeInTheDocument();
  });

  it('AC-011.7 selects the Member role and loads another role when it is chosen', async () => {
    const { user, dialog } = await openPopup();

    expect(within(dialog).getByText('Member')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('combobox', { name: 'Jira role' }));
    await user.click(await screen.findByTitle('Administrator'));

    expect(await within(dialog).findByRole('row', { name: /Quân Admin/ })).toBeInTheDocument();
  });

  it('AC-011.8 lets only addable people be ticked and greys the others with their reason', async () => {
    const { dialog } = await openPopup();

    expect(within(dialog).getByRole('checkbox', { name: 'Select Bảo Phạm' })).toBeEnabled();
    const lan = within(dialog).getByRole('row', { name: /Lan Trần/ });
    expect(within(lan).getByText(JIRA_TAB_TEXTS.reasons.ALREADY_MEMBER)).toBeInTheDocument();
    expect(within(lan).getByRole('checkbox')).toBeDisabled();
    const guest = within(dialog).getByRole('row', { name: /Guest Contractor/ });
    expect(within(guest).getByText(JIRA_TAB_TEXTS.reasons.NOT_LINKED)).toBeInTheDocument();
    expect(within(guest).getByRole('checkbox')).toBeDisabled();
  });

  it('AC-011.9 names the groups of the role', async () => {
    const { dialog } = await openPopup();

    expect(
      within(dialog).getByText(JIRA_TAB_TEXTS.groups(['it-support-team'])),
    ).toBeInTheDocument();
  });

  it('AC-011.10 shows MSG04 for a role with nobody in it', async () => {
    const { user, dialog } = await openPopup();

    await user.click(within(dialog).getByRole('combobox', { name: 'Jira role' }));
    await user.click(await screen.findByTitle('Viewer'));

    expect(await within(dialog).findByText(msg('MSG04'))).toBeInTheDocument();
  });

  it('AC-011.10 says suggestions are not available and keeps the Search tab working', async () => {
    server.use(http.get('*/api/v1/projects/:projectId/jira-roles', () => problem(503, 'MSG06')));
    const user = userEvent.setup();
    renderApp(paths.projectDetail(ITSUP_ID), { role: 'PROJECT_MANAGER' });
    await user.click(await screen.findByRole('button', { name: 'Add member' }, { timeout: 5000 }));
    const dialog = await screen.findByRole('dialog', { name: 'Add member' });

    expect(await within(dialog).findByText(JIRA_TAB_TEXTS.notAvailable)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('tab', { name: 'Search' }));
    expect(await within(dialog).findByRole('row', { name: /Phạm Quốc Bảo/ })).toBeInTheDocument();
  });

  it('AC-011.6 AC-011.11 adds the people ticked on both tabs in one request', async () => {
    const adds = recordAdds();
    const { user, dialog } = await openPopup();

    await user.click(within(dialog).getByRole('checkbox', { name: 'Select Bảo Phạm' }));
    await user.click(within(dialog).getByRole('tab', { name: 'Search' }));
    await user.click(await within(dialog).findByRole('checkbox', { name: 'Select Ngô Thị Mai' }));
    await user.click(within(dialog).getByRole('tab', { name: 'From Jira' }));
    expect(within(dialog).getByRole('checkbox', { name: 'Select Bảo Phạm' })).toBeChecked();
    await user.click(within(dialog).getByRole('button', { name: 'Add' }));

    await waitFor(() => expect(adds).toHaveLength(1));
    expect([...(adds[0] as { userIds: string[] }).userIds].sort()).toEqual([BAO, MAI].sort());
    expect(await screen.findByText(msg('MSG05'))).toBeInTheDocument();
  });
});
