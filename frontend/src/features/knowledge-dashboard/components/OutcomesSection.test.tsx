import { screen } from '@testing-library/react';
import type { DashboardOutcomes } from '@/shared/api';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { OutcomesSection } from './OutcomesSection';

const OUTCOMES: DashboardOutcomes = {
  approved: 6,
  rejected: 2,
  changesRequested: 4,
  approvalRate: 0.5,
  pendingReview: 3,
  expired: 1,
  perDay: [
    { date: '2026-11-01', approved: 4, rejected: 2, changesRequested: 1 },
    { date: '2026-11-02', approved: 2, rejected: 0, changesRequested: 3 },
  ],
  perProject: [
    {
      projectId: '00000000-0000-4000-8000-000000000101',
      name: 'ITSUP',
      approved: 6,
      rejected: 2,
      changesRequested: 4,
    },
  ],
};

describe('OutcomesSection', () => {
  it('AC-005.6 shows totals as statistics, the approval rate as a progress bar and bars per day and Project', () => {
    renderWithProviders(<OutcomesSection outcomes={OUTCOMES} />);
    expect(screen.getByText('Pending review')).toBeInTheDocument();
    expect(
      screen.getByText('Changes requested', { selector: '.ant-statistic-title' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Approval rate' })).toHaveAttribute(
      'aria-valuenow',
      '50',
    );
    expect(screen.getByRole('list', { name: 'Outcomes per day' })).toHaveTextContent(
      '01/11/2026Approved 4 · Rejected 2 · Changes requested 1',
    );
    expect(screen.getByRole('list', { name: 'Outcomes per Project' })).toHaveTextContent(
      'ITSUPApproved 6 · Rejected 2 · Changes requested 4',
    );
  });

  it('AC-005.8 shows MSG04 when the period has no outcome', () => {
    renderWithProviders(
      <OutcomesSection
        outcomes={{
          ...OUTCOMES,
          approved: 0,
          rejected: 0,
          changesRequested: 0,
          approvalRate: null,
          pendingReview: 0,
          expired: 0,
          perProject: [],
        }}
      />,
    );
    expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});
