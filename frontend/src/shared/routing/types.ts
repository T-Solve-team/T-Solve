import type { ComponentType } from 'react';
import type { Role } from '@/shared/auth/roles';

/**
 * One screen of a feature (guideline 08 §2). The app turns these into React Router routes behind the
 * sign-in and role guards.
 */
export type FeatureRoute = {
  /** From `paths` — never a typed string. */
  path: string;
  /** SRS screen name, shown in the AppShell header and the browser tab. */
  title: string;
  /**
   * Who may open it: a user with any of these roles. ['EMPLOYEE'] = every signed-in user (each has Employee,
   * GBR-28). 'GUEST' = signed-out users only (Login). Required, so no screen is unguarded.
   */
  roles: readonly Role[] | 'GUEST';
  /** Loads the page module only when the screen is opened. */
  lazy: () => Promise<{ default: ComponentType }>;
};
