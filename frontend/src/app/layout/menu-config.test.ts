import { MENUS, selectedMenuPath } from './menu-config';

describe('MENUS', () => {
  it('matches the approved menus per role, landing page first', () => {
    const labels = (role: keyof typeof MENUS) => MENUS[role].map((entry) => entry.label);
    expect(labels('TICKET_RESOLVER')).toEqual(['Search', 'Ticket List']);
    expect(labels('PROJECT_MANAGER')).toEqual([
      'Review Queue',
      'Review History',
      'Expired Tickets',
      'Search',
      'Ticket List',
      'Import Tickets',
      'Import History',
      'Knowledge Dashboard',
      'Jira Integration',
    ]);
    // Employee and Knowledge Manager items arrive with the Wiki and Chatbot routes (KAN-61).
    expect(labels('EMPLOYEE')).toEqual([]);
    expect(labels('KNOWLEDGE_MANAGER')).toEqual([]);
    expect(labels('ADMIN')).toEqual([
      'User List',
      'Workspace Settings',
      'Jira Integration',
      'Audit Log',
      'Knowledge Dashboard',
    ]);
  });
});

describe('selectedMenuPath', () => {
  const pm = MENUS.PROJECT_MANAGER;

  it('highlights the list for a detail screen', () => {
    expect(selectedMenuPath(pm, '/tickets/42')).toBe('/tickets');
  });

  it('prefers the longest matching path', () => {
    expect(selectedMenuPath(pm, '/import/history')).toBe('/import/history');
    expect(selectedMenuPath(pm, '/import/preview')).toBe('/import');
  });

  it('highlights nothing for screens outside the menu', () => {
    expect(selectedMenuPath(pm, '/profile')).toBeNull();
  });
});
