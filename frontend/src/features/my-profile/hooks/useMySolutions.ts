import { keepPreviousData } from '@tanstack/react-query';
import { useGetMySolutions } from '@/shared/api';

/** One page of the user's Solutions. No retry: a failure shows MSG06 or MSG08 in the card (AC-003.14). */
export function useMySolutions(page: number) {
  return useGetMySolutions(
    { page: page - 1, size: 20 },
    { query: { retry: false, placeholderData: keepPreviousData } },
  );
}
