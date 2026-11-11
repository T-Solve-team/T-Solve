import { screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import type { DashboardFeedback } from '@/shared/api';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderWithProviders } from '@/test/render';
import { FeedbackSection } from './FeedbackSection';

const TOP = '00000000-0000-4000-8000-000000000501';
const FEEDBACK: DashboardFeedback = {
  helpful: 9,
  notHelpful: 3,
  helpfulRate: 0.75,
  topRated: [
    {
      ticketId: TOP,
      title: 'Restore access to the shared drive',
      helpful: 7,
      notHelpful: 0,
      helpfulRate: 1,
    },
  ],
  lowestRated: [
    {
      ticketId: '00000000-0000-4000-8000-000000000502',
      title: 'Reset an expired VPN certificate',
      helpful: 2,
      notHelpful: 3,
      helpfulRate: 0.4,
    },
  ],
};

function renderSection(feedback: DashboardFeedback, canOpenTickets: boolean) {
  return renderWithProviders(
    <MemoryRouter>
      <FeedbackSection feedback={feedback} canOpenTickets={canOpenTickets} />
    </MemoryRouter>,
  );
}

describe('FeedbackSection', () => {
  it('AC-005.6 shows the totals, the helpful rate and the top and bottom 5 tickets as tables', () => {
    renderSection(FEEDBACK, true);
    expect(
      screen.getByText('Not helpful', { selector: '.ant-statistic-title' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Helpful rate' })).toHaveAttribute(
      'aria-valuenow',
      '75',
    );
    const bottom = screen.getByRole('table', { name: 'Bottom 5 tickets' });
    expect(within(bottom).getByText('Reset an expired VPN certificate')).toBeInTheDocument();
    expect(within(bottom).getByText('40%')).toBeInTheDocument();
  });

  it('AC-005.9 links the top and bottom 5 tickets to Ticket Detail for a Project Manager', () => {
    renderSection(FEEDBACK, true);
    expect(
      screen.getByRole('link', { name: 'Restore access to the shared drive' }),
    ).toHaveAttribute('href', paths.ticketDetail(TOP));
    expect(
      screen.getByRole('link', { name: 'Reset an expired VPN certificate' }),
    ).toBeInTheDocument();
  });

  it('AC-005.9 shows the tickets as plain text for an Admin who is not a Project Manager', () => {
    renderSection(FEEDBACK, false);
    expect(screen.getByText('Restore access to the shared drive')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('AC-005.8 shows MSG04 when the period has no feedback', () => {
    renderSection(
      { helpful: 0, notHelpful: 0, helpfulRate: null, topRated: [], lowestRated: [] },
      true,
    );
    expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
