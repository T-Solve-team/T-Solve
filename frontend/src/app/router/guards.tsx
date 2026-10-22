import { Navigate, Outlet, useLocation } from 'react-router';
import { LANDING_PATHS, mainRole, useSession, type Role } from '@/shared/auth';
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
  if (session) return <Navigate to={LANDING_PATHS[mainRole(session.roles)]} replace />;
  return <Outlet />;
}

/** "/" → the landing page of the signed-in user. */
export function LandingRedirect() {
  const { session } = useSession();
  return session ? <Navigate to={LANDING_PATHS[mainRole(session.roles)]} replace /> : null;
}
