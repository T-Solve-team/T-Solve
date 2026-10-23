import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { AppProviders } from '@/app/providers/AppProviders';
import { createAppQueryClient } from '@/app/providers/global-errors';
import { buildRoutes } from '@/app/router/routes';
import { DEV_USER_FOR_ROLE, setDevUser, type DevUserId, type Role } from '@/shared/auth';

/**
 * Who the `/me` mock says is signed in (src/mocks/session.ts): either `role` — the fixture user with exactly that
 * role (plus Employee); null = signed out; default Project Manager — or `user`, a fixture user by id
 * (e.g. 'pm-km'). Never both.
 */
type RenderOptions =
  { role?: Role | null; user?: never } | { user: DevUserId | null; role?: never };

function signIn(options: RenderOptions): void {
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
  const router = createMemoryRouter(buildRoutes(), { initialEntries: [path] });
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
