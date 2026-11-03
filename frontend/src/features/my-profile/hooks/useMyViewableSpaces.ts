import { useGetMyViewableSpaces } from '@/shared/api';

/** The Spaces the user can view (AC-003.3). No retry: a failure shows MSG06 in the card (AC-003.14). */
export function useMyViewableSpaces() {
  return useGetMyViewableSpaces({ query: { retry: false } });
}
