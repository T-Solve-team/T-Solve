import type { ReviewDecision, TicketStatus } from '@/shared/api';
import { seedReviewTickets } from './data';

export type ReviewHistoryEntry = {
  decisionId: string;
  ticketId: string;
  actorId: string;
  actorName: string;
  action: ReviewDecision;
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
