import { paths, type FeatureRoute } from '@/shared/routing';

/** Screens of the "knowledge-dashboard" feature (guideline 08 §2). */
export const knowledgeDashboardRoutes: FeatureRoute[] = [
  {
    path: paths.knowledgeDashboard,
    title: 'Knowledge Dashboard', // 9.1
    roles: ['PROJECT_MANAGER', 'ADMIN'],
    lazy: () => import('./pages/KnowledgeDashboardPage'),
  },
];
