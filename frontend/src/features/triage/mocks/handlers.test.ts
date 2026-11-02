import {
  getSuggestionResult,
  getSuggestionResults,
  GetSuggestionResultsAnswer,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';

const ITSUP_ID = '00000000-0000-4000-8000-000000000101';
const HRHELP_ID = '00000000-0000-4000-8000-000000000201';

describe('suggestion history mock contract', () => {
  beforeEach(() => setDevUser('pm'));

  it('AC-001.2 returns a newest-first page of 20 across managed Projects', async () => {
    const first = await getSuggestionResults({ page: 0, size: 20 });
    const second = await getSuggestionResults({ page: 1, size: 20 });
    expect(first.content).toHaveLength(20);
    expect(second.content.length).toBeGreaterThan(0);
    expect(first.page.totalElements).toBeGreaterThan(20);
    expect(first.content.every((entry) => entry.project.projectId !== HRHELP_ID)).toBe(true);
    const [newest, next] = first.content;
    if (!newest || !next) throw new Error('Expected at least two history rows');
    expect(newest.createdAt >= next.createdAt).toBe(true);
  });

  it('AC-001.3 filters Project and answer together (BUG-093)', async () => {
    const filtered = await getSuggestionResults({
      projectId: ITSUP_ID,
      answer: GetSuggestionResultsAnswer.YES,
    });
    expect(filtered.content.length).toBeGreaterThan(0);
    expect(
      filtered.content.every(
        (entry) => entry.project.projectId === ITSUP_ID && entry.reporterAnswer === 'YES',
      ),
    ).toBe(true);
    const noAnswer = await getSuggestionResults({ answer: GetSuggestionResultsAnswer.NONE });
    expect(noAnswer.content.every((entry) => entry.reporterAnswer === null)).toBe(true);
  });

  it('AC-001.5 rejects malformed or partial date ranges', async () => {
    for (const params of [
      { from: '2026-02-30', to: '2026-03-01' },
      { from: '2026-11-01' },
      { from: '2026-11-02', to: '2026-11-01' },
    ]) {
      await expect(getSuggestionResults(params)).rejects.toMatchObject({
        status: 400,
        code: 'MSG39',
      });
    }
  });

  it('AC-001.6 includes the last milliseconds of a Vietnam day', async () => {
    const firstDay = await getSuggestionResults({ from: '2026-11-01', to: '2026-11-01' });
    const secondDay = await getSuggestionResults({ from: '2026-11-02', to: '2026-11-02' });
    expect(firstDay.content.some((entry) => entry.issueKey === 'ITNET-2001')).toBe(true);
    expect(firstDay.content.some((entry) => entry.issueKey === 'ITNET-2002')).toBe(false);
    expect(secondDay.content.some((entry) => entry.issueKey === 'ITNET-2002')).toBe(true);
  });

  it('AC-001.10 denies an unsigned or non-Project Manager caller', async () => {
    setDevUser('employee');
    await expect(getSuggestionResults()).rejects.toMatchObject({ status: 403, code: 'MSG08' });
    setDevUser(null);
    await expect(getSuggestionResults()).rejects.toMatchObject({ status: 401, code: 'MSG07' });
  });

  it('AC-001.11 rejects unmanaged Projects and ignores a forged Workspace selector', async () => {
    await expect(getSuggestionResults({ projectId: HRHELP_ID })).rejects.toMatchObject({
      status: 403,
      code: 'MSG08',
    });
    const response = await fetch('/api/v1/suggestion-results?workspaceId=foreign');
    expect(response.status).toBe(200);
    const body = await response.text();
    expect(body).not.toContain('Foreign Workspace suggestion');
  });

  it('AC-002.7 hides inaccessible detail existence', async () => {
    await expect(getSuggestionResult('HRHELP-3001')).rejects.toMatchObject({
      status: 404,
      code: 'MSG04',
    });
    await expect(getSuggestionResult('ITSUP-9999')).rejects.toMatchObject({
      status: 404,
      code: 'MSG04',
    });
  });

  it('AC-002.3 returns only Ticket source counts and Wiki references', async () => {
    const detail = await getSuggestionResult('ITSUP-1001');
    expect(detail.ticketSourceCount).toBe(2);
    expect(detail.wikiSources).toHaveLength(2);
    expect(JSON.stringify(detail)).not.toContain('ticketSources');
    expect(detail.wikiSources.some((source) => !source.isAvailable)).toBe(true);
  });
});
