import { AuditAction, getAuditEvents } from '@/shared/api';
import { setDevSite, setDevUser } from '@/shared/auth';

describe('audit mock contract', () => {
  beforeEach(() => setDevUser('admin'));

  it('AC-001.11 scopes before totals, sorting and pagination and strips tenant metadata', async () => {
    const first = await getAuditEvents({ page: 0, size: 20 });
    const second = await getAuditEvents({ page: 1, size: 20 });
    expect(first.content).toHaveLength(20);
    expect(second.content).toHaveLength(15);
    expect(first.page).toEqual({ number: 0, size: 20, totalElements: 35, totalPages: 2 });
    const entries = [...first.content, ...second.content];
    expect(new Set(entries.map((entry) => entry.id)).size).toBe(35);
    expect(entries.some((entry) => entry.summary === 'Foreign Workspace event')).toBe(false);
    expect(entries.every((entry) => !Object.hasOwn(entry, 'workspaceId'))).toBe(true);
    expect(new Set(entries.map((entry) => entry.action))).toEqual(
      new Set(Object.values(AuditAction)),
    );
    for (let index = 1; index < entries.length; index++) {
      const previous = entries[index - 1];
      const current = entries[index];
      expect(
        previous &&
          current &&
          (previous.createdAt > current.createdAt ||
            (previous.createdAt === current.createdAt && previous.id < current.id)),
      ).toBe(true);
    }
  });

  it('AC-001.17 combines trimmed email substring, exact action and dates with AND', async () => {
    const result = await getAuditEvents({
      actor: '  DUONG.PHAM ',
      action: AuditAction.APPROVE,
      from: '2026-10-01',
      to: '2026-10-30',
    });
    expect(result.page.totalElements).toBe(4);
    expect(
      result.content.every(
        (entry) => entry.actor === 'duong.pham@company.example' && entry.action === 'APPROVE',
      ),
    ).toBe(true);
    expect((await getAuditEvents({ actor: 'lan', action: AuditAction.APPROVE })).content).toEqual(
      [],
    );
    expect((await getAuditEvents({ actor: '  ' })).page.totalElements).toBe(35);
  });

  it('AC-001.15 includes fractional seconds on the last selected Vietnam day only', async () => {
    const endDay = await getAuditEvents({ from: '2026-10-01', to: '2026-10-01' });
    expect(endDay.content.map((entry) => entry.actor)).toEqual(['former.user@company.example']);
    expect((await getAuditEvents({ from: '2026-10-02', to: '2026-10-02' })).content).toEqual([]);
  });

  it('AC-001.16 rejects invalid date pairs without returning a broader list', async () => {
    for (const params of [
      { from: '2026-02-30', to: '2026-03-01' },
      { from: '2026-10-01' },
      { from: '2026-10-02', to: '2026-10-01' },
      { from: '2099-01-01', to: '2099-01-01' },
    ]) {
      await expect(getAuditEvents(params)).rejects.toMatchObject({ status: 400, code: 'MSG39' });
    }
  });

  it('AC-001.9 rejects direct mock access without Admin', async () => {
    setDevUser('employee');
    await expect(getAuditEvents()).rejects.toMatchObject({ status: 403, code: 'MSG08' });
    setDevUser(null);
    await expect(getAuditEvents()).rejects.toMatchObject({ status: 401, code: 'MSG07' });
    setDevUser('admin-tr');
    expect((await getAuditEvents()).content.length).toBe(20);
  });

  it('AC-001.10 never lets forged tenant query params select another Workspace', async () => {
    const response = await fetch(
      '/api/v1/audit-events?workspaceId=00000000-0000-4000-8000-0000000000c2',
    );
    const body: unknown = await response.json();
    expect(response.status).toBe(200);
    expect(JSON.stringify(body)).not.toContain('Foreign Workspace event');
    expect(JSON.stringify(body)).toContain('"totalElements":35');
  });

  it('AC-001.8 uses the global site-unavailable contract', async () => {
    setDevSite('unknown-site');
    await expect(getAuditEvents()).rejects.toMatchObject({ status: 404, code: 'MSG119' });
  });
});
