import { searchTickets, type SearchTicketsParams } from '@/shared/api';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
/** The fixture users' Departments (src/mocks/session.ts). */
const IT_ID = '00000000-0000-4000-8000-0000000000d1';
const HR_ID = '00000000-0000-4000-8000-0000000000d2';

async function titles(params: SearchTicketsParams): Promise<string[]> {
  const page = await searchTickets({ size: 100, ...params });
  return page.content.map((t) => t.title);
}

describe('search mock handlers', () => {
  it('AC-004.3 returns the Published tickets whose title or Solution text contains the query, in any case', async () => {
    expect(await titles({ q: 'VPN' })).toEqual([
      'VPN disconnects every 10 minutes on home Wi-Fi',
      'VPN cannot connect after the Windows update',
      'Remote desktop over VPN is very slow',
    ]);
    expect(await titles({ q: 'automapping' })).toEqual(['Shared mailbox not visible in Outlook']); // excerpt
    expect(await titles({ q: 'FRAGMENTATION' })).toEqual(['Remote desktop over VPN is very slow']); // only in the Solution
    expect(await titles({ q: 'zzz' })).toEqual([]);
  });

  it('AC-004.3 returns typed results with UUID ids, and helpfulRate null for a ticket without Feedback', async () => {
    const page = await searchTickets({ q: 'e', size: 100 });
    expect(page.page.totalElements).toBe(14);
    for (const t of page.content) {
      expect(t.ticketId).toMatch(UUID);
      expect(t).not.toHaveProperty('solution');
      expect(t).not.toHaveProperty('source');
    }
    const rates = page.content.map((t) => t.helpfulRate);
    expect(rates.filter((r) => r === null)).toHaveLength(2);
    expect(rates).toContain(0);
  });

  it('AC-004.3 filters by Department and source', async () => {
    expect(await titles({ q: 'e', departmentId: HR_ID, source: 'MANUAL' })).toEqual([
      'Cannot download tax statement',
    ]);
    expect(await titles({ q: 'vpn', departmentId: IT_ID, source: 'JIRA_IMPORT' })).toEqual([
      'VPN cannot connect after the Windows update',
      'Remote desktop over VPN is very slow',
    ]);
  });

  it('AC-004.3 filters by the Resolved date range, both days included', async () => {
    expect(
      await titles({ q: 'vpn', resolvedFrom: '2026-09-15', resolvedTo: '2026-09-29' }),
    ).toEqual([
      'VPN cannot connect after the Windows update',
      'Remote desktop over VPN is very slow',
    ]);
    expect(await titles({ q: 'vpn', resolvedFrom: '2026-10-02' })).toEqual([
      'VPN disconnects every 10 minutes on home Wi-Fi',
    ]);
    expect(await titles({ q: 'vpn', resolvedTo: '2026-09-15' })).toEqual([
      'Remote desktop over VPN is very slow',
    ]);
  });

  it('AC-004.3 pages the results', async () => {
    const second = await searchTickets({ q: 'e', page: 1, size: 10 });
    expect(second.content).toHaveLength(4);
    expect(second.page).toEqual({ size: 10, number: 1, totalElements: 14, totalPages: 2 });
  });
});
