import { screen } from '@testing-library/react';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { CoverageSection } from './CoverageSection';

describe('CoverageSection', () => {
  it('AC-005.6 shows the totals and the Published share overall and per Department', () => {
    renderWithProviders(
      <CoverageSection
        coverage={{
          publishedTickets: 24,
          allTickets: 40,
          publishedShare: 0.6,
          perDepartment: [
            {
              departmentId: '00000000-0000-4000-8000-0000000000d1',
              name: 'IT',
              publishedTickets: 24,
              allTickets: 40,
              publishedShare: 0.6,
            },
          ],
        }}
      />,
    );
    expect(screen.getByText('All tickets')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Published share' })).toHaveAttribute(
      'aria-valuenow',
      '60',
    );
    expect(screen.getByRole('progressbar', { name: 'IT: 24 of 40 Published' })).toBeInTheDocument();
  });

  it('AC-005.8 shows MSG04 when the scope has no ticket', () => {
    renderWithProviders(
      <CoverageSection
        coverage={{ publishedTickets: 0, allTickets: 0, publishedShare: null, perDepartment: [] }}
      />,
    );
    expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
  });
});
