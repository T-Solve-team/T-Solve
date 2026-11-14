import { screen, within } from '@testing-library/react';
import { renderWithProviders } from '@/test/render';
import { BarList } from './BarList';

describe('BarList', () => {
  it('AC-005.6 draws each bar scaled to the largest total and writes its values as text', () => {
    renderWithProviders(
      <BarList
        label="Outcomes per Project"
        series={['Approved', 'Rejected']}
        bars={[
          { key: 'a', label: 'ITSUP', values: [6, 2] },
          { key: 'b', label: 'ITNET', values: [2, 0] },
        ]}
      />,
    );
    const rows = within(screen.getByRole('list', { name: 'Outcomes per Project' })).getAllByRole(
      'listitem',
    );
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('ITSUPApproved 6 · Rejected 2');
    expect(rows[1]).toHaveTextContent('ITNETApproved 2 · Rejected 0');
    const widths = (row: HTMLElement) =>
      Array.from(row.querySelectorAll<HTMLElement>('[aria-hidden] > span')).map(
        (segment) => segment.style.width,
      );
    expect(widths(rows[0] as HTMLElement)).toEqual(['75%', '25%']);
    expect(widths(rows[1] as HTMLElement)).toEqual(['25%', '0%']);
  });
});
