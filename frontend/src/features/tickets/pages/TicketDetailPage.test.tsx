import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { ReviewTicketDetail, TicketStatus } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { formatDate, formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const ticketId = '00000000-0000-4000-8000-000000000135';
const endpoint = '*/api/v1/tickets/:ticketId';
const reviewDueDate = '2026-11-18T00:00:00+07:00';

function showTicket(overrides: Partial<ReviewTicketDetail> = {}) {
  const ticket = {
    ticketId,
    sourceTicketId: 'DETAIL-135',
    title: 'Login restored',
    description: 'Ticket description',
    project: { projectId: ticketId, name: 'DETAIL' },
    department: { id: ticketId, name: 'Customer Support' },
    source: 'SOLUTION_FORM',
    status: 'PUBLISHED',
    authorId: null,
    authorName: 'Ticket writer',
    resolvedDate: '2026-11-14',
    sourceLink: 'https://example.test/browse/DETAIL-135',
    reviewDueDate,
    solution: { symptoms: 'Login failed', rootCause: 'Token', resolutionSteps: 'Refresh token' },
    evidence: [],
    lastReviewComment: null,
    canDecide: false,
    ...overrides,
  };
  server.use(http.get(endpoint, () => HttpResponse.json(ticket)));
  return renderApp(`/tickets/${ticketId}`, { role: 'TICKET_RESOLVER' });
}

describe('TicketDetailPage', () => {
  it.each(['https://example.test/ticket', 'http://example.test/ticket'])(
    'AC-008.8 shows Department, resolved date and a safe source link: %s',
    async (sourceLink) => {
      showTicket({ sourceLink });
      expect(
        await screen.findByText('Customer Support', {}, { timeout: 5000 }),
      ).toBeInTheDocument();
      expect(screen.getByText(formatDate('2026-11-14'))).toBeInTheDocument();
      const link = screen.getByRole('link', { name: sourceLink });
      expect(link).toHaveAttribute('href', sourceLink);
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', 'noopener noreferrer');
      expect(screen.getByText('Refresh token')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    },
  );

  it.each([
    'javascript:alert(1)',
    'data:text/html,test',
    '//example.test/ticket',
    '/ticket',
    'invalid',
  ])('AC-008.8 shows a non-http source as plain text: %s', async (sourceLink) => {
    showTicket({ sourceLink });
    expect(await screen.findByText(sourceLink, {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: sourceLink })).not.toBeInTheDocument();
  });

  it('AC-008.8 tolerates absent Department, resolved date and source link', async () => {
    showTicket({ department: null, resolvedDate: null, sourceLink: null });
    await screen.findByText('Refresh token', {}, { timeout: 5000 });
    for (const label of ['Department', 'Resolved date', 'Source link']) {
      expect(screen.getByText(label).closest('tr')).toHaveTextContent('—');
    }
  });

  it('AC-008.9 shows the review due date while Pending review', async () => {
    showTicket({ status: 'PENDING_REVIEW' });
    expect(await screen.findByText('Review due date', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText(formatDateTime(reviewDueDate))).toBeInTheDocument();
  });

  it.each<TicketStatus>(['PUBLISHED', 'REJECTED', 'CHANGES_REQUESTED', 'EXPIRED', 'UNPUBLISHED'])(
    'AC-008.9 hides the review due date for %s',
    async (status) => {
      showTicket({ status });
      await screen.findByText('Refresh token', {}, { timeout: 5000 });
      expect(screen.queryByText('Review due date')).not.toBeInTheDocument();
      expect(screen.queryByText(formatDateTime(reviewDueDate))).not.toBeInTheDocument();
    },
  );

  it('AC-008.10 shows MSG08 inline without ticket content or a toast on 403', async () => {
    server.use(http.get(endpoint, () => problem(403, 'MSG08')));
    renderApp(`/tickets/${ticketId}`, { role: 'TICKET_RESOLVER' });
    expect(await screen.findByText(msg('MSG08'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByText('Solution')).not.toBeInTheDocument();
    expect(screen.queryByText('DETAIL-135')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
    expect(document.querySelector('.ant-message')).not.toBeInTheDocument();
  });

  it('AC-008.10 keeps MSG04 inline without a toast on 404', async () => {
    server.use(http.get(endpoint, () => problem(404, 'MSG04')));
    renderApp(`/tickets/${ticketId}`, { role: 'TICKET_RESOLVER' });
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    expect(document.querySelector('.ant-message')).not.toBeInTheDocument();
  });

  it('AC-008.10 shows the MSG06 toast and retry state on 500', async () => {
    server.use(http.get(endpoint, () => problem(500, 'MSG06')));
    renderApp(`/tickets/${ticketId}`, { role: 'TICKET_RESOLVER' });
    expect(
      await screen.findByRole('button', { name: 'Try again' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    const alerts = await screen.findAllByRole('alert');
    expect(alerts.some((alert) => within(alert).queryByText(msg('MSG06')) !== null)).toBe(true);
    await waitFor(() =>
      expect(document.querySelector('.ant-message')).toHaveTextContent(msg('MSG06')),
    );
    expect(screen.queryByText('Solution')).not.toBeInTheDocument();
  });
});
