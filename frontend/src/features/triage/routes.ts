import { paths, type FeatureRoute } from '@/shared/routing';

// Popups of this feature (no route): 10.2 Triage Result Detail (popup).

/** Screens of the "triage" feature (guideline 08 §2). */
export const triageRoutes: FeatureRoute[] = [
  {
    path: paths.triageHistory,
    title: 'Triage History', // 10.1
    roles: ['PROJECT_MANAGER'],
    lazy: () => import('./pages/TriageHistoryPage'),
  },
];
