import { menuFor, MENUS, selectedMenuPath } from './menu-config';

const labels = (entries: readonly { label: string }[]) => entries.map((entry) => entry.label);

describe('MENUS', () => {
  it('AC-004.1 has the menu of each role in the order of the UI decisions (brief §3)', () => {
    expect(labels(MENUS.EMPLOYEE)).toEqual(['Knowledge Chatbot', 'Wiki']);
    expect(labels(MENUS.TICKET_RESOLVER)).toEqual(['Search', 'Ticket List']);
    expect(labels(MENUS.PROJECT_MANAGER)).toEqual([
      'Review Queue',
      'Expired Tickets',
      'Review History',
      'Search',
      'Ticket List',
      'Import Tickets',
      'Import History',
      'Triage History',
      'Knowledge Dashboard',
      'Jira Integration',
    ]);
    expect(labels(MENUS.KNOWLEDGE_MANAGER)).toEqual(['Wiki', 'Knowledge Chatbot']);
    expect(labels(MENUS.ADMIN)).toEqual([
      'User List',
      'Workspace Settings',
      'Jira Integration',
      'Knowledge Dashboard',
      'Audit Log',
    ]);
  });
});

describe('menuFor', () => {
  it('AC-004.2 lists Admin, Project Manager, Knowledge Manager, Ticket Resolver, Employee items, each once', () => {
    expect(labels(menuFor(['EMPLOYEE', 'PROJECT_MANAGER', 'KNOWLEDGE_MANAGER']))).toEqual([
      'Review Queue',
      'Expired Tickets',
      'Review History',
      'Search',
      'Ticket List',
      'Import Tickets',
      'Import History',
      'Triage History',
      'Knowledge Dashboard',
      'Jira Integration',
      'Wiki',
      'Knowledge Chatbot',
    ]);
    expect(labels(menuFor(['EMPLOYEE', 'ADMIN', 'TICKET_RESOLVER']))).toEqual([
      'User List',
      'Workspace Settings',
      'Jira Integration',
      'Knowledge Dashboard',
      'Audit Log',
      'Search',
      'Ticket List',
      'Knowledge Chatbot',
      'Wiki',
    ]);
  });

  it('AC-004.2 gives an Employee-only user the Employee menu', () => {
    expect(labels(menuFor(['EMPLOYEE']))).toEqual(['Knowledge Chatbot', 'Wiki']);
  });
});

describe('selectedMenuPath', () => {
  const pm = MENUS.PROJECT_MANAGER;

  it('AC-004.3 highlights the list for a detail screen', () => {
    expect(selectedMenuPath(pm, '/tickets/42')).toBe('/tickets');
  });

  it('AC-004.3 prefers the longest matching path', () => {
    expect(selectedMenuPath(pm, '/import/history')).toBe('/import/history');
    expect(selectedMenuPath(pm, '/import/preview')).toBe('/import');
  });

  it('AC-004.4 highlights nothing for screens outside the menu', () => {
    expect(selectedMenuPath(pm, '/profile')).toBeNull();
  });
});
