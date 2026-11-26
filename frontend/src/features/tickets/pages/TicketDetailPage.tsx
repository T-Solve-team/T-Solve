/** 5.5 Ticket Detail — SRS III.5.5 */
import { Flex } from 'antd';
import { useParams } from 'react-router';
import { GiveFeedbackButton } from '@/features/feedback';
import { ReviewTicketDetail } from '@/features/review';
import { TicketPublicationActions } from '../components/TicketPublicationActions';

export function TicketDetailPage() {
  const { ticketId = '' } = useParams();
  return (
    <Flex vertical gap="large">
      <TicketPublicationActions key={ticketId} ticketId={ticketId} />
      <GiveFeedbackButton key={`feedback-${ticketId}`} ticketId={ticketId} />
      <ReviewTicketDetail key={`detail-${ticketId}`} ticketId={ticketId} />
    </Flex>
  );
}

export default TicketDetailPage;
