import { paths, type FeatureRoute } from '@/shared/routing';

// Popups of this feature (no route): none.

/** Screens of the "audit-log" feature (guideline 08 §2). */
export const auditLogRoutes: FeatureRoute[] = [
  {
    path: paths.auditLog,
    title: 'Audit Log', // 9.2
    roles: ['ADMIN'],
    lazy: () => import('./pages/AuditLogPage'),
  },
];
