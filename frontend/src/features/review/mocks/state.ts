import type { TicketStatus } from '@/shared/api';
import { seedReviewTickets } from './data';

export type ReviewHistoryEntry = {
  ticketId: string;
  actorId: string;
  action: 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES' | 'SEND_BACK';
  timestamp: string;
  beforeStatus: TicketStatus;
  afterStatus: TicketStatus;
  reason: string | null;
  comment: string | null;
};

/** Session-only fixture state; this is not durable audit history or Jira delivery. */
export const reviewStore = {
  tickets: seedReviewTickets(),
  history: [] as ReviewHistoryEntry[],
};

export function resetReviewState(): void {
  reviewStore.tickets = seedReviewTickets();
  reviewStore.history.length = 0;
}
