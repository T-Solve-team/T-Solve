import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { paths } from '@/shared/routing';
import { renderApp } from '@/test/render';

describe('AuditActionLabel', () => {
  it('AC-001.16 labels the DEPARTMENT_CHANGE action "Department change" on the Audit Log screen', async () => {
    const user = userEvent.setup();
    renderApp(paths.auditLog, { role: 'ADMIN' });
    await screen.findByRole('table', { name: 'Audit log' }, { timeout: 5000 });
    await user.click(screen.getByRole('combobox', { name: 'Action' }));
    expect(await screen.findByRole('option', { name: 'Department change' })).toBeInTheDocument();
  });
});
