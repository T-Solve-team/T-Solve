import { Button, Flex } from 'antd';
import { useState } from 'react';
import { useGetReviewTicketDetail } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { GiveFeedbackModal } from './GiveFeedbackModal';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** Opens Give Feedback (SRS 8.1 / UC-28) from Ticket Detail. Covers AC-003.12 and AC-003.13. */
export function GiveFeedbackButton({ ticketId }: { ticketId: string }) {
  const detail = useGetReviewTicketDetail(ticketId, {
    query: { enabled: UUID_PATTERN.test(ticketId) },
  });
  const { hasRole } = useSession();
  const [isOpen, setIsOpen] = useState(false);

  if (
    detail.isError ||
    detail.data?.status !== 'PUBLISHED' ||
    (!hasRole('TICKET_RESOLVER') && !hasRole('PROJECT_MANAGER'))
  ) {
    return null;
  }

  return (
    <Flex>
      <Button onClick={() => setIsOpen(true)}>Give feedback</Button>
      <GiveFeedbackModal ticketId={ticketId} isOpen={isOpen} onClose={() => setIsOpen(false)} />
    </Flex>
  );
}
