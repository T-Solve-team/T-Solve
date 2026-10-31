import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { AppProviders } from '@/app/providers/AppProviders';
import { createAppQueryClient } from '@/app/providers/global-errors';
import { buildRoutes } from '@/app/router/routes';
import {
  DEV_USER_FOR_ROLE,
  setDevSite,
  setDevUser,
  type DevSiteId,
  type DevUserId,
  type Role,
} from '@/shared/auth';

/**
 * Who the `/me` mock says is signed in (src/mocks/session.ts): either `role` — the fixture user with exactly that
 * role (plus Employee); null = signed out; default Project Manager — or `user`, a fixture user by id
 * (e.g. 'pm-km'). Never both.
 */
type RenderOptions = (
  { role?: Role | null; user?: never } | { user: DevUserId | null; role?: never }
) & {
  /**
   * `'unknown'` / `'suspended'`: every mock API request answers MSG119 (404) / MSG120 (403), like the dev user
   * switcher's "Unknown site" / "Suspended Workspace". The site state is reset after each test (setup.ts).
   */
  site?: 'unknown' | 'suspended';
  /**
   * The host the app is opened on, a hostname without port (default `'localhost'`, a Workspace host). Only
   * `renderApp` uses it: it chooses the route tree (STORY-013). It is not case-normalised here.
   */
  host?: string;
};

const DEV_SITE_FOR: Record<NonNullable<RenderOptions['site']>, DevSiteId> = {
  unknown: 'unknown-site',
  suspended: 'suspended-workspace',
};

function signIn(options: RenderOptions): void {
  if (options.site !== undefined) {
    setDevSite(DEV_SITE_FOR[options.site]);
    return;
  }
  if (options.user !== undefined) {
    setDevUser(options.user);
    return;
  }
  const role = options.role === undefined ? 'PROJECT_MANAGER' : options.role;
  setDevUser(role ? DEV_USER_FOR_ROLE[role] : null);
}

/** The app's real QueryClient (global error handling included) without retries, fresh per test. */
export function createTestQueryClient() {
  const client = createAppQueryClient();
  client.setDefaultOptions({ queries: { retry: false }, mutations: { retry: false } });
  return client;
}

/** Renders the whole app at `path` — real routes, guards, AppShell and MSW mocks (guideline 10). */
export function renderApp(path: string, options: RenderOptions = {}) {
  signIn(options);
  const router = createMemoryRouter(buildRoutes(options.host ?? 'localhost'), {
    initialEntries: [path],
  });
  const queryClient = createTestQueryClient();
  const result = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...result, router, queryClient };
}

/** Renders one component with the app providers (theme, antd, query cache, MSW mocks). */
export function renderWithProviders(ui: ReactElement, options: RenderOptions = {}) {
  signIn(options);
  const queryClient = createTestQueryClient();
  return { ...render(<AppProviders queryClient={queryClient}>{ui}</AppProviders>), queryClient };
}
