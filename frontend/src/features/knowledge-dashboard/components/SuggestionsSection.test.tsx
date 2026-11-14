import { screen } from '@testing-library/react';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { SuggestionsSection } from './SuggestionsSection';

describe('SuggestionsSection', () => {
  it('AC-005.6 shows posted and solved suggestions and the solved share', () => {
    renderWithProviders(
      <SuggestionsSection suggestions={{ posted: 10, solved: 7, solvedShare: 0.7 }} />,
    );
    expect(screen.getByText('Posted')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Solved share' })).toHaveAttribute(
      'aria-valuenow',
      '70',
    );
  });

  it('AC-005.8 shows MSG04 when no suggestion was posted in the period', () => {
    renderWithProviders(
      <SuggestionsSection suggestions={{ posted: 0, solved: 0, solvedShare: null }} />,
    );
    expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
  });
});
