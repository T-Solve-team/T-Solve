import { screen } from '@testing-library/react';
import { chatbotRoutes } from '@/features/chatbot';
import { triageRoutes } from '@/features/triage';
import { wikiRoutes } from '@/features/wiki';
import type { Role } from '@/shared/auth';
import type { FeatureRoute } from '@/shared/routing';
import { renderApp } from '@/test/render';
import { featureRoutes } from './feature-routes';

type Row = readonly [number: string, title: string, path: string, roles: readonly Role[] | 'GUEST'];

/** The screen table of spec AC-005.1 (Permission Matrix, UC list 4.4). */
const SCREENS: readonly Row[] = [
  ['1.1.1', 'Login', '/login', 'GUEST'],
  ['1.2.1', 'My Profile', '/profile', ['EMPLOYEE']],
  ['1.3.1', 'User List', '/users', ['ADMIN']],
  ['1.3.2', 'User Detail', '/users/new', ['ADMIN']],
  ['1.3.2', 'User Detail', '/users/:userId', ['ADMIN']],
  ['2.1', 'Workspace Setup', '/workspace-setup', ['ADMIN']],
  ['2.2', 'Workspace Settings', '/workspace-settings', ['ADMIN']],
  ['2.3', 'Department Detail', '/workspace-settings/departments/:departmentId', ['ADMIN']],
  ['3.1', 'Jira Integration', '/jira-integration', ['ADMIN', 'PROJECT_MANAGER']],
  ['3.2', 'Project Detail', '/jira-integration/projects/:projectId', ['PROJECT_MANAGER']],
  ['5.1', 'Import Tickets', '/import', ['PROJECT_MANAGER']],
  ['5.2', 'Import Preview', '/import/preview', ['PROJECT_MANAGER']],
  ['5.4', 'Import History', '/import/history', ['PROJECT_MANAGER']],
  ['5.5', 'Ticket Detail', '/tickets/:ticketId', ['TICKET_RESOLVER', 'PROJECT_MANAGER']],
  ['6.1', 'Search', '/search', ['TICKET_RESOLVER', 'PROJECT_MANAGER']],
  ['6.2', 'Ticket List', '/tickets', ['TICKET_RESOLVER', 'PROJECT_MANAGER']],
  ['7.1', 'Review Queue', '/review-queue', ['PROJECT_MANAGER']],
  ['7.4', 'Review History', '/review-history', ['PROJECT_MANAGER']],
  ['7.5', 'Expired Tickets', '/expired-tickets', ['PROJECT_MANAGER']],
  ['9.1', 'Knowledge Dashboard', '/dashboard', ['PROJECT_MANAGER', 'ADMIN']],
  ['9.2', 'Audit Log', '/audit-log', ['ADMIN']],
  ['10.1', 'Triage History', '/triage-history', ['PROJECT_MANAGER']],
  ['11.1', 'Wiki Home', '/wiki', ['EMPLOYEE']],
  ['11.2', 'Wiki Document', '/wiki/documents/:documentId', ['EMPLOYEE']],
  ['11.4', 'Upload Documents', '/wiki/upload', ['KNOWLEDGE_MANAGER']],
  ['11.7', 'Space Settings', '/wiki/spaces/:spaceId/settings', ['KNOWLEDGE_MANAGER']],
  ['12.1', 'Knowledge Chatbot', '/chatbot', ['EMPLOYEE']],
];

/** Popups of AC-005.2: no path, named in their feature's routes.ts. */
const POPUPS = [
  ['users', '1.3.3 Hand Over Responsibilities'],
  ['workspace', '2.4 Delete Department'],
  ['ticket-import', '5.3 Import Ticket Preview'],
  ['review', '7.2 Request Changes'],
  ['review', '7.3 Reject Ticket'],
  ['feedback', '8.1 Give Feedback'],
  ['triage', '10.2 Triage Result Detail'],
  ['wiki', '11.5 Folder'],
] as const;

const routesSource = import.meta.glob<string>('/src/features/*/routes.ts', {
  query: '?raw',
  import: 'default',
  eager: true,
});

const byPath = (path: string) => featureRoutes.find((route) => route.path === path);
const sortedRoles = (roles: FeatureRoute['roles']) =>
  roles === 'GUEST' ? roles : [...roles].sort();

describe('route map', () => {
  it('AC-005.1 has exactly the routed screens of the spec table, with their paths and titles', () => {
    expect(featureRoutes.map((route) => [route.title, route.path])).toEqual(
      expect.arrayContaining(SCREENS.map(([, title, path]) => [title, path])),
    );
    expect(featureRoutes).toHaveLength(SCREENS.length);
  });

  it.each(SCREENS)(
    'AC-002.5 %s %s lists exactly the roles of the spec table',
    (_n, title, path, roles) => {
      const route = byPath(path);
      expect(route?.title).toBe(title);
      expect(sortedRoles(route?.roles ?? [])).toEqual(sortedRoles(roles));
    },
  );

  it.each(POPUPS)(
    'AC-005.2 names the popup %s / %s in routes.ts and gives it no path',
    (feature, popup) => {
      expect(routesSource[`/src/features/${feature}/routes.ts`]).toContain(popup);
      const name = popup.replace(/^[\d.]+ /, '');
      expect(featureRoutes.some((route) => route.title === name)).toBe(false);
    },
  );

  it('AC-005.2 does not name 9.3 Audit Entry Detail in audit-log/routes.ts', () => {
    expect(routesSource['/src/features/audit-log/routes.ts']).not.toContain('Audit Entry Detail');
  });

  it('AC-005.2 names exactly 8 popups in the routes.ts files', () => {
    const named = Object.values(routesSource).flatMap((source) =>
      (source.match(/^\/\/ Popups of this feature.*$/gm) ?? []).flatMap(
        (line) => line.match(/\d+(?:\.\d+)+ [A-Z][A-Za-z ]+?(?= \(popup\)|,|\.|$)/g) ?? [],
      ),
    );
    expect(named).toHaveLength(8);
  });

  // The placeholder screens not built yet: a module that builds its screen removes its row (12.1: chatbot, KAN-71).
  it.each([
    ['2.1', 'Workspace Setup', '/workspace-setup', 'ADMIN'],
    ['10.1', 'Triage History', '/triage-history', 'PROJECT_MANAGER'],
    ['11.1', 'Wiki Home', '/wiki', 'EMPLOYEE'],
    ['11.2', 'Wiki Document', '/wiki/documents/7', 'EMPLOYEE'],
    ['11.4', 'Upload Documents', '/wiki/upload', 'KNOWLEDGE_MANAGER'],
    ['11.7', 'Space Settings', '/wiki/spaces/1/settings', 'KNOWLEDGE_MANAGER'],
  ] as const)('AC-005.3 shows the placeholder of %s %s', async (number, title, path, role) => {
    renderApp(path, { role });
    // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
    expect(
      await screen.findByRole('heading', { level: 1, name: title }, { timeout: 5000 }),
    ).toBeInTheDocument();
    const escapedNumber = number.replaceAll('.', '\\.');
    expect(
      screen.getByText(new RegExp(`^${escapedNumber} ${title} — not built yet`)),
    ).toBeInTheDocument();
  });

  // Their mocks are checked in src/mocks/handlers.test.ts (app/ may not import a feature's mocks).
  it('AC-005.4 registers the triage, wiki and chatbot routes like every other feature', () => {
    for (const routes of [triageRoutes, wikiRoutes, chatbotRoutes]) {
      for (const route of routes) expect(featureRoutes).toContain(route);
    }
  });
});
