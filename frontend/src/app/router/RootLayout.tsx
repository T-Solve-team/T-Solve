import { lazy, Suspense, useEffect } from 'react';
import { Outlet } from 'react-router';
import { useSiteStatus } from '@/shared/api';
import { SiteUnavailablePage } from '../pages/SiteUnavailablePage';
import { usePageTitle } from './usePageTitle';

// Dev builds in mock mode only (with a real backend the session cookie decides who you are).
// `import.meta.env.…` must be written literally here: Vite replaces it with `false` in production builds
// and drops the switcher's code entirely.
const DevUserSwitcher =
  import.meta.env.DEV && import.meta.env.VITE_API_MOCKING === 'true'
    ? lazy(() => import('../dev/DevUserSwitcher').then((m) => ({ default: m.DevUserSwitcher })))
    : null;

export function RootLayout() {
  const title = usePageTitle();
  const { site } = useSiteStatus();

  // Browser tab: "<screen name> · T-Solve" (syncs with the document, an external system — guideline 05 C7).
  useEffect(() => {
    document.title = title ? `${title} · T-Solve` : 'T-Solve';
  }, [title]);

  return (
    <>
      {/* An unknown site or a Suspended Workspace replaces every screen, Login included (STORY-011). */}
      {site === 'ok' ? <Outlet /> : <SiteUnavailablePage site={site} />}
      {DevUserSwitcher && (
        <Suspense fallback={null}>
          <DevUserSwitcher />
        </Suspense>
      )}
    </>
  );
}
