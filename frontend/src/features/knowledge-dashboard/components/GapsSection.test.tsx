import { screen, within } from '@testing-library/react';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { GapsSection } from './GapsSection';

describe('GapsSection', () => {
  it('AC-005.6 shows the searches and Knowledge Chatbot questions with no result as tables', () => {
    renderWithProviders(
      <GapsSection
        gaps={{
          noResultSearches: [{ query: 'VPN certificate renewal', count: 3 }],
          chatbotNoSource: 4,
          chatbotQuestions: [{ question: 'How do I renew my VPN certificate?', count: 4 }],
        }}
      />,
    );
    const searches = screen.getByRole('table', { name: 'Searches with no result' });
    expect(within(searches).getByText('VPN certificate renewal')).toBeInTheDocument();
    expect(within(searches).getByText('3')).toBeInTheDocument();
    const questions = screen.getByRole('table', {
      name: 'Knowledge Chatbot questions with no source',
    });
    expect(within(questions).getByText('How do I renew my VPN certificate?')).toBeInTheDocument();
  });

  it('AC-005.8 shows MSG04 when the period has no gap', () => {
    renderWithProviders(
      <GapsSection gaps={{ noResultSearches: [], chatbotNoSource: 0, chatbotQuestions: [] }} />,
    );
    expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('AC-005.11 keeps the scope note when the section is empty', () => {
    renderWithProviders(
      <GapsSection
        gaps={{ noResultSearches: [], chatbotNoSource: 0, chatbotQuestions: [] }}
        note="Figures count the whole Workspace."
      />,
    );
    expect(screen.getByText('Figures count the whole Workspace.')).toBeInTheDocument();
  });
});
