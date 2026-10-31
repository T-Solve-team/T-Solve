import { useGetTickets, type GetTicketsParams } from '@/shared/api';

/**
 * One page of the Ticket List (SRS 6.2). No placeholder data: every new filter, sort or page shows the loading
 * state (design, builder check; spec AC-001.11).
 */
export function useTicketList(params: GetTicketsParams) {
  return useGetTickets(params);
}
