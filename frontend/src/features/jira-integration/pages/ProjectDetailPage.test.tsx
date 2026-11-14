import { screen, within } from '@testing-library/react';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';
import { ITNET_ID, ITSUP_ID } from '../mocks/data';

const UNKNOWN_ID = '00000000-0000-4000-8000-000000000999';

async function openMembers(projectId = ITSUP_ID) {
  renderApp(paths.projectDetail(projectId), { role: 'PROJECT_MANAGER' });
  return screen.findByRole('table', { name: 'Members' }, { timeout: 5000 });
}

describe('ProjectDetailPage', () => {
  it('AC-001.5 shows the header and the Members tab with Added on as a date in the app time zone', async () => {
    const table = await openMembers();
    expect(screen.getByText('ITSUP')).toBeInTheDocument();
    expect(screen.getByText('IT')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    const managerLabel = screen.getByText('Project Manager', {
      selector: '.ant-descriptions-item-label',
    });
    expect(managerLabel.closest('.ant-descriptions-item')).toHaveTextContent('Đỗ Minh Quân');
    expect(screen.getByRole('tab', { name: 'Members' })).toBeInTheDocument();
    const headers = within(table)
      .getAllByRole('columnheader')
      .map((header) => header.textContent);
    expect(headers).toEqual(['Full name', 'Email', 'Role', 'Added on']);
    const lan = within(table).getByRole('row', { name: /Trần Thị Lan/ });
    // 2026-10-01T18:30:00Z is 2 October in Vietnam (UTC+7).
    expect(within(lan).getByText('02/10/2026')).toBeInTheDocument();
    expect(within(lan).getByText('lan.tran@company.example')).toBeInTheDocument();
    expect(within(lan).getByText('Member')).toBeInTheDocument();
    const manager = within(table).getByRole('row', { name: /quan.do@company.example/ });
    expect(within(manager).getByText('Project Manager')).toBeInTheDocument();
  });

  it('AC-001.6 shows MSG08 in place of the screen when the backend answers 403', async () => {
    server.use(http.get('*/api/v1/projects/:projectId', () => problem(403, 'MSG08')));
    renderApp(paths.projectDetail(ITSUP_ID), { role: 'PROJECT_MANAGER' });
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Members' })).not.toBeInTheDocument();
  });

  it('AC-001.6 shows MSG04 when the Project does not exist (404)', async () => {
    renderApp(paths.projectDetail(UNKNOWN_ID), { role: 'PROJECT_MANAGER' });
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Members' })).not.toBeInTheDocument();
  });

  it('AC-001.6 shows MSG06 when the request fails otherwise', async () => {
    server.use(http.get('*/api/v1/projects/:projectId/members', () => problem(500)));
    renderApp(paths.projectDetail(ITSUP_ID), { role: 'PROJECT_MANAGER' });
    expect(await screen.findByText(msg('MSG06'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Members' })).not.toBeInTheDocument();
  });

  it('AC-001.7 shows no Remove button in the Members tab', async () => {
    await openMembers();
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument();
  });

  it('AC-002.9 shows "Add member" while the Project is Active', async () => {
    await openMembers();
    expect(screen.getByRole('button', { name: 'Add member' })).toBeInTheDocument();
  });

  it('AC-002.9 hides "Add member" while the Project is Removed', async () => {
    await openMembers(ITNET_ID);
    expect(screen.getByText('Removed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add member' })).not.toBeInTheDocument();
  });
});
