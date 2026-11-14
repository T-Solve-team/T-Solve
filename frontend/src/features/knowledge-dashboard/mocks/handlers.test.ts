import { getKnowledgeDashboard } from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { presetRange } from '../hooks/useDashboardFilters';

describe('knowledge dashboard mock', () => {
  beforeEach(() => setDevUser('admin'));

  it('AC-005.10 returns realistic figures for every section and the requested period', async () => {
    const period = { from: '2026-01-01', to: '2026-01-07' };
    const data = await getKnowledgeDashboard(period);
    expect(data.period).toEqual(period);
    expect(data.outcomes.perDay).toHaveLength(7);
    expect(data.outcomes.perDay[0]?.date).toBe(period.from);
    expect(data.outcomes.perDay[6]?.date).toBe(period.to);
    expect(data.outcomes.approved).toBe(
      data.outcomes.perDay.reduce((sum, day) => sum + day.approved, 0),
    );
    expect(data.outcomes.perProject).toHaveLength(3);
    expect(data.coverage.publishedTickets).toBeGreaterThan(0);
    expect(data.coverage.perDepartment).toHaveLength(2);
    expect(data.feedback.topRated).toHaveLength(1);
    expect(data.feedback.lowestRated).toHaveLength(1);
    expect(data.gaps.noResultSearches).toHaveLength(1);
    expect(data.gaps.chatbotQuestions).toHaveLength(1);
    expect(data.suggestions.posted).toBeGreaterThan(data.suggestions.solved);
  });

  it('AC-005.10 defaults to 30 days and accepts 366 inclusive days', async () => {
    expect((await getKnowledgeDashboard()).period).toEqual(presetRange('30d'));
    const data = await getKnowledgeDashboard({ from: '2024-01-01', to: '2024-12-31' });
    expect(data.outcomes.perDay).toHaveLength(366);
  });

  it.each([
    { from: '2026-01-01' },
    { to: '2026-01-01' },
    { from: '', to: '' },
    { from: '2026-02-30', to: '2026-03-01' },
    { from: '2026-02-02', to: '2026-02-01' },
    { from: '2099-01-01', to: '2099-01-02' },
    { from: '2024-01-01', to: '2025-01-01' },
  ])('AC-005.10 rejects invalid dates with 400 MSG39: %j', async (period) => {
    await expect(getKnowledgeDashboard(period)).rejects.toMatchObject({
      status: 400,
      code: 'MSG39',
    });
  });

  it.each([
    { departmentId: 'bad' },
    { departmentId: '' },
    { projectId: '00000000000040008000000000000101' },
    { projectId: ' 00000000-0000-4000-8000-000000000101' },
  ])('AC-005.7 rejects a malformed id with 400 MSG06 as the backend does: %j', async (filters) => {
    await expect(getKnowledgeDashboard(filters)).rejects.toMatchObject({
      status: 400,
      code: 'MSG06',
    });
  });

  it('AC-005.7 answers 404 MSG04 for an unknown id to an Admin and 403 MSG08 to a Project Manager', async () => {
    const unknown = '00000000-0000-4000-8000-000000000999';
    for (const filters of [{ departmentId: unknown }, { projectId: unknown }]) {
      setDevUser('admin');
      await expect(getKnowledgeDashboard(filters)).rejects.toMatchObject({
        status: 404,
        code: 'MSG04',
      });
      setDevUser('pm');
      await expect(getKnowledgeDashboard(filters)).rejects.toMatchObject({
        status: 403,
        code: 'MSG08',
      });
    }
  });

  it('AC-005.4 offers the Project Manager Department and its Projects', async () => {
    setDevUser('pm-km');
    const data = await getKnowledgeDashboard();
    expect(data.options.departments.map((item) => item.name)).toEqual(['HR']);
    expect(data.options.projects.map((item) => item.name)).toEqual(['HRHELP']);
    expect(data.outcomes.perProject.map((item) => item.name)).toEqual(['HRHELP']);
  });

  it('AC-005.10 filters figures by Department and Project while keeping all allowed options', async () => {
    const all = await getKnowledgeDashboard();
    const project = all.options.projects[0];
    if (!project) throw new Error('Expected a Project option');
    const department = await getKnowledgeDashboard({ departmentId: project.departmentId });
    const data = await getKnowledgeDashboard({
      departmentId: project.departmentId,
      projectId: project.projectId,
    });
    expect(data.options).toEqual(all.options);
    expect(data.outcomes.perProject.map((item) => item.projectId)).toEqual([project.projectId]);
    expect(data.gaps).toEqual(department.gaps);
  });
});
