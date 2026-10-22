import { Navigate, Outlet, useLocation } from 'react-router';
import { landingPath, useSession, type Role } from '@/shared/auth';
import { paths } from '@/shared/routing';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import { SessionErrorPage } from '../pages/SessionErrorPage';
import { FullPageLoading } from './FullPageLoading';

/** Signed-in users only; others go to Login and come back afterwards (guideline 08 §3). */
export function RequireAuth() {
  const { session, isPending, isError, retry } = useSession();
  const location = useLocation();
  if (isPending) return <FullPageLoading />;
  if (isError) return <SessionErrorPage onRetry={() => void retry()} />;
  if (!session) {
    const returnTo = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`${paths.login}?returnTo=${returnTo}`} replace />;
  }
  return <Outlet />;
}

/** The screen's allowed roles; anyone else sees 403 with MSG08 in place. */
export function RequireRole({ roles }: { roles: readonly Role[] }) {
  const { hasAnyRole } = useSession();
  if (!hasAnyRole(roles)) return <ForbiddenPage />;
  return <Outlet />;
}

/** Login is for signed-out users; a signed-in user goes to their landing page. */
export function GuestOnly() {
  const { session, isPending, isError, retry } = useSession();
  if (isPending) return <FullPageLoading />;
  if (isError) return <SessionErrorPage onRetry={() => void retry()} />;
  if (session) return <Navigate to={landingPath(session.roles)} replace />;
  return <Outlet />;
}

/** "/" → the landing page of the signed-in user. */
export function LandingRedirect() {
  const { session } = useSession();
  return session ? <Navigate to={landingPath(session.roles)} replace /> : null;
}

/** Screens an Admin may open while the Workspace is not set up (AC-003.2). */
const OPEN_BEFORE_SETUP: readonly string[] = [paths.workspaceSetup, paths.profile];

/**
 * While the Workspace has no company information (`workspaceReady` false), an Admin is sent to 2.1 Workspace
 * Setup first (UC-10, MSG60); other users are not redirected (AC-003.3).
 */
export function RequireWorkspace() {
  const { session, hasRole } = useSession();
  const { pathname } = useLocation();
  if (
    session &&
    !session.workspaceReady &&
    hasRole('ADMIN') &&
    !OPEN_BEFORE_SETUP.includes(pathname)
  ) {
    return <Navigate to={paths.workspaceSetup} replace />;
  }
  return <Outlet />;
}
