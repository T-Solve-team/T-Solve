import { paths, type FeatureRoute } from '@/shared/routing';

/** The Jira link confirm page: routed outside the guards by the app (it checks the session itself). */
export const jiraLinkPage = () => import('./pages/JiraLinkPage');

/** Screens of the "auth" feature (guideline 08 §2). */
export const authRoutes: FeatureRoute[] = [
  {
    path: paths.login,
    title: 'Login', // 1.1.1
    roles: 'GUEST',
    lazy: () => import('./pages/LoginPage'),
  },
];
