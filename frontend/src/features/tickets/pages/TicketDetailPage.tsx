/** 5.5 Ticket Detail — SRS III.5.5 */
import { useParams } from 'react-router';
import { ReviewTicketDetail } from '@/features/review';

export function TicketDetailPage() {
  const { ticketId = '' } = useParams();
  return <ReviewTicketDetail key={ticketId} ticketId={ticketId} />;
}

export default TicketDetailPage;
