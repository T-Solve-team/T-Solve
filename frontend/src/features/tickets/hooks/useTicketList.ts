import { keepPreviousData } from '@tanstack/react-query';
import { useGetTickets, type GetTicketsParams } from '@/shared/api';

/** One page of the Ticket List (SRS 6.2). */
export function useTicketList(params: GetTicketsParams) {
  return useGetTickets(params, {
    query: { placeholderData: keepPreviousData }, // no flicker when the page, sort or a filter changes
  });
}
