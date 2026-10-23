import { paths, type FeatureRoute } from '@/shared/routing';

/** Screens of the "jira-integration" feature (guideline 08 §2). */
export const jiraIntegrationRoutes: FeatureRoute[] = [
  {
    path: paths.jiraIntegration,
    title: 'Jira Integration', // 3.1
    roles: ['ADMIN', 'PROJECT_MANAGER'],
    lazy: () => import('./pages/JiraIntegrationPage'),
  },
  {
    path: paths.projectDetail(),
    title: 'Project Detail', // 3.2
    roles: ['PROJECT_MANAGER'],
    lazy: () => import('./pages/ProjectDetailPage'),
  },
];
