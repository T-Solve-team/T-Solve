import type { RouteObject } from 'react-router';
import type { FeatureRoute } from '@/shared/routing';
import { AppShell } from '../layout/AppShell';
import { NotFoundPage } from '../pages/NotFoundPage';
import { RouteErrorPage } from '../pages/RouteErrorPage';
import { platformConsoleRoutes } from '@/features/platform-console';
import { featureRoutes } from './feature-routes';
import { FullPageLoading } from './FullPageLoading';
import { GuestOnly, LandingRedirect, RequireAuth, RequireRole, RequireWorkspace } from './guards';
import { isPlatformConsoleHost } from './host';
import { RootLayout } from './RootLayout';

function toPageRoute(route: FeatureRoute): RouteObject {
  return {
    index: true,
    lazy: async () => {
      const { default: Component } = await route.lazy();
      return { Component };
    },
  };
}

/** Platform Console host: only the Console's routes, and the 404 page for any other path (STORY-013). */
function buildConsoleRoutes(): RouteObject[] {
  return [
    {
      element: <RootLayout />,
      errorElement: <RouteErrorPage />,
      hydrateFallbackElement: <FullPageLoading />,
      children: [
        ...platformConsoleRoutes,
        { path: '*', handle: { title: 'Page not found' }, element: <NotFoundPage /> },
      ],
    },
  ];
}

/**
 * Route tree chosen by host (guideline 08): the Platform Console host gets only the Console routes; every other
 * host gets the Workspace tree below — guest screens · signed-in screens inside the AppShell, behind the Workspace Setup
 * redirect and each behind its role guard · "/" → landing page · unknown paths → 404. Built from `featureRoutes`.
 */
export function buildRoutes(
  host = 'localhost',
  routes: readonly FeatureRoute[] = featureRoutes,
): RouteObject[] {
  if (isPlatformConsoleHost(host)) return buildConsoleRoutes();
  const guest = routes.flatMap((route) => (route.roles === 'GUEST' ? [route] : []));
  const signedIn = routes.flatMap((route) =>
    route.roles === 'GUEST' ? [] : [{ ...route, roles: route.roles }],
  );

  return [
    {
      element: <RootLayout />,
      errorElement: <RouteErrorPage />,
      // Shown while the first page's lazy module loads (opening a deep link or reloading).
      hydrateFallbackElement: <FullPageLoading />,
      children: [
        {
          element: <GuestOnly />,
          children: guest.map((route) => ({
            path: route.path,
            handle: { title: route.title },
            children: [toPageRoute(route)],
          })),
        },
        // Dev builds only: the mock sign-in page, reachable signed out and signed in (Q5).
        ...(import.meta.env.DEV
          ? [
              {
                path: '/dev/sign-in',
                handle: { title: 'Mock sign-in (dev only)' },
                lazy: () =>
                  import('../dev/MockSignInPage').then((m) => ({ Component: m.MockSignInPage })),
              },
            ]
          : []),
        {
          element: <RequireAuth />,
          children: [
            {
              element: <AppShell />,
              children: [
                {
                  element: <RequireWorkspace />,
                  children: [
                    { index: true, element: <LandingRedirect /> },
                    ...signedIn.map((route) => ({
                      path: route.path,
                      handle: { title: route.title },
                      element: <RequireRole roles={route.roles} />,
                      children: [toPageRoute(route)],
                    })),
                  ],
                },
                // Dev builds only: every kit component on one page (guideline 07 §3). `import.meta.env.DEV`
                // is written literally so production builds drop the page.
                ...(import.meta.env.DEV
                  ? [
                      {
                        path: '/dev/kit',
                        handle: { title: 'Kit (dev only)' },
                        lazy: () =>
                          import('../dev/KitPage').then((m) => ({ Component: m.KitPage })),
                      },
                    ]
                  : []),
                { path: '*', handle: { title: 'Page not found' }, element: <NotFoundPage /> },
              ],
            },
          ],
        },
      ],
    },
  ];
}
