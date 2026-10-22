import { landingPath, type Role } from '@/shared/auth';

describe('landingPath', () => {
  it.each([
    [['EMPLOYEE', 'ADMIN'], '/users'],
    [['EMPLOYEE', 'PROJECT_MANAGER'], '/review-queue'],
    [['EMPLOYEE', 'KNOWLEDGE_MANAGER'], '/wiki'],
    [['EMPLOYEE', 'TICKET_RESOLVER'], '/search'],
    [['EMPLOYEE'], '/chatbot'],
    [['EMPLOYEE', 'PROJECT_MANAGER', 'KNOWLEDGE_MANAGER'], '/review-queue'],
    [['EMPLOYEE', 'ADMIN', 'TICKET_RESOLVER'], '/users'],
    [['EMPLOYEE', 'TICKET_RESOLVER', 'KNOWLEDGE_MANAGER'], '/wiki'],
  ] as [Role[], string][])('AC-003.1 sends roles %j to %s (first match)', (roles, path) => {
    expect(landingPath(roles)).toBe(path);
  });
});
