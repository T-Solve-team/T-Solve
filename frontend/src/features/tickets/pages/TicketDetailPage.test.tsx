import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { ReviewTicketResponse } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

let ticket: ReviewTicketResponse;
beforeEach(() => {
  ticket = {
    ticketId: 1,
    sourceTicketId: 'ITSUP-1',
    title: 'VPN repair',
    project: { projectId: 101, name: 'IT Support' },
    source: 'SOLUTION_FORM',
    writerId: 'writer',
    authorName: 'Writer',
    status: 'PENDING_REVIEW',
    enteredQueueAt: '2026-10-01T00:00:00Z',
    reviewDueDate: '2026-10-04T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    solution: { symptoms: 'VPN fails', resolutionSteps: 'Renew certificate' },
    evidence: [{ evidenceId: 'e1', name: 'error.png', mediaType: 'image/png', sizeBytes: 128 }],
  };
  server.use(http.get('*/api/v1/tickets/1', () => HttpResponse.json(ticket)));
});

describe('Ticket Detail display', () => {
  it('shows Solution and Evidence read-only', async () => {
    renderApp('/tickets/1');
    await screen.findByRole('heading', { name: 'VPN repair' }, { timeout: 5000 });
    expect(screen.getByText('Renew certificate')).toBeInTheDocument();
    expect(screen.getByText(/error.png/)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('shows an access error with retry', async () => {
    server.use(http.get('*/api/v1/tickets/1', () => problem(403)));
    renderApp('/tickets/1');
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG08'));
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
