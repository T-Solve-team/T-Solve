import { paths, type FeatureRoute } from '@/shared/routing';

// Popups of this feature (no route): 1.3.3 Hand Over Responsibilities (popup).

/** Screens of the "users" feature (guideline 08 §2). */
export const usersRoutes: FeatureRoute[] = [
  {
    path: paths.userList,
    title: 'User List', // 1.3.1
    roles: ['ADMIN'],
    lazy: () => import('./pages/UserListPage'),
  },
  {
    path: paths.userNew,
    title: 'User Detail', // 1.3.2
    roles: ['ADMIN'],
    lazy: () => import('./pages/UserDetailPage'),
  },
  {
    path: paths.userDetail(),
    title: 'User Detail', // 1.3.2
    roles: ['ADMIN'],
    lazy: () => import('./pages/UserDetailPage'),
  },
];
