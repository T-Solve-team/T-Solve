import { paths, type FeatureRoute } from '@/shared/routing';

/** Screens of the "tickets" feature (guideline 08 §2). */
export const ticketsRoutes: FeatureRoute[] = [
  {
    path: paths.ticketList,
    title: 'Ticket List', // 6.2
    roles: ['TICKET_RESOLVER', 'PROJECT_MANAGER'],
    lazy: () => import('./pages/TicketListPage'),
  },
  {
    path: paths.ticketDetail(),
    title: 'Ticket Detail', // 5.5
    roles: ['TICKET_RESOLVER', 'PROJECT_MANAGER'],
    lazy: () => import('./pages/TicketDetailPage'),
  },
];
