import { paths, type FeatureRoute } from '@/shared/routing';

// Popups of this feature (no route): 2.4 Delete Department (popup).

/** Screens of the "workspace" feature (guideline 08 §2). */
export const workspaceRoutes: FeatureRoute[] = [
  {
    path: paths.workspaceSetup,
    title: 'Workspace Setup', // 2.1
    roles: ['ADMIN'],
    lazy: () => import('./pages/WorkspaceSetupPage'),
  },
  {
    path: paths.workspaceSettings,
    title: 'Workspace Settings', // 2.2
    roles: ['ADMIN'],
    lazy: () => import('./pages/WorkspaceSettingsPage'),
  },
  {
    path: paths.departmentDetail(),
    title: 'Department Detail', // 2.3
    roles: ['ADMIN'],
    lazy: () => import('./pages/DepartmentDetailPage'),
  },
];
