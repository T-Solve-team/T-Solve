import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '@/shared/api';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { DataTable, type DataTableColumn } from './DataTable';

type Row = { id: number; name: string };
const COLUMNS: DataTableColumn<Row>[] = [
  { key: 'name', title: 'Name', sortable: true, render: (_, r) => r.name },
];

describe('DataTable', () => {
  it('shows the rows and the Figma pagination total', () => {
    renderWithProviders(
      <DataTable<Row>
        label="Things"
        columns={COLUMNS}
        rows={[{ id: 1, name: 'First' }]}
        rowKey={(r) => r.id}
        pagination={{ page: 1, total: 58, onChange: () => undefined }}
      />,
    );
    expect(screen.getByRole('cell', { name: 'First' })).toBeInTheDocument();
    expect(screen.getByText('Total 58 items')).toBeInTheDocument();
  });

  it("shows the screen's empty message", () => {
    renderWithProviders(
      <DataTable<Row>
        label="Things"
        columns={COLUMNS}
        rows={[]}
        rowKey={(r) => r.id}
        emptyCode="MSG13"
      />,
    );
    expect(screen.getByText(msg('MSG13'))).toBeInTheDocument();
  });

  it('shows the error with a retry instead of the table', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    renderWithProviders(
      <DataTable<Row>
        label="Things"
        columns={COLUMNS}
        rows={undefined}
        rowKey={(r) => r.id}
        error={new ApiError({ status: 403, code: 'MSG08' })}
        onRetry={onRetry}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent(msg('MSG08'));
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('reports a click on a sortable header as a server sort', async () => {
    const user = userEvent.setup();
    const onSortChange = vi.fn();
    renderWithProviders(
      <DataTable<Row>
        label="Things"
        columns={COLUMNS}
        rows={[{ id: 1, name: 'First' }]}
        rowKey={(r) => r.id}
        sort={null}
        onSortChange={onSortChange}
      />,
    );
    await user.click(screen.getByText('Name'));
    expect(onSortChange).toHaveBeenCalledWith({ field: 'name', order: 'asc' });
  });
});
