import { paths, type FeatureRoute } from '@/shared/routing';

/** Screens of the "search" feature (guideline 08 §2). */
export const searchRoutes: FeatureRoute[] = [
  {
    path: paths.search,
    title: 'Search', // 6.1
    roles: ['TICKET_RESOLVER', 'PROJECT_MANAGER'],
    lazy: () => import('./pages/SearchPage'),
  },
];
