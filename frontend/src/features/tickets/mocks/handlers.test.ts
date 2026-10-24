import { getTickets, TicketSource, TicketStatus, type GetTicketsParams } from '@/shared/api';

async function ids(params: GetTicketsParams): Promise<string[]> {
  const page = await getTickets({ size: 100, ...params });
  return page.content.map((t) => t.sourceTicketId);
}

describe('tickets mock handlers', () => {
  it('AC-002.1 returns at least 25 tickets with every status, every source and two Departments', async () => {
    const page = await getTickets({ size: 100 });
    const tickets = page.content;
    expect(page.page.totalElements).toBeGreaterThanOrEqual(25);
    expect(new Set(tickets.map((t) => t.status))).toEqual(new Set(Object.values(TicketStatus)));
    expect(new Set(tickets.map((t) => t.source))).toEqual(new Set(Object.values(TicketSource)));
    expect(new Set(tickets.map((t) => t.department.departmentId)).size).toBeGreaterThanOrEqual(2);
    expect(tickets[0]).not.toHaveProperty('solution');
  });

  it('AC-002.2 matches the keyword on ticket ID, title and Solution text, in any case', async () => {
    expect(await ids({ keyword: 'itsup-1031' })).toEqual(['ITSUP-1031']);
    expect(await ids({ keyword: 'PAYSLIP' })).toEqual(['HRHELP-0318']);
    expect(await ids({ keyword: 'split tunnelling' })).toEqual(['ITSUP-1102']); // only in the Solution
  });

  it('AC-002.2 filters by Department, source and status together', async () => {
    const page = await getTickets({
      size: 100,
      departmentId: 2,
      source: 'SOLUTION_FORM',
      status: 'PUBLISHED',
    });
    expect(page.content.map((t) => t.sourceTicketId)).toEqual(['HRHELP-0232']);
  });

  it('AC-002.2 sorts by updatedAt newest first by default and by any sort key', async () => {
    const byDefault = await getTickets({ size: 100 });
    const updated = byDefault.content.map((t) => t.updatedAt);
    expect(updated).toEqual([...updated].sort().reverse());
    expect((await ids({ sort: ['sourceTicketId,asc'] }))[0]).toBe('HRHELP-0232');
  });

  it('AC-002.2 pages the result', async () => {
    const second = await getTickets({ page: 1, size: 20 });
    expect(second.content).toHaveLength(6);
    expect(second.page).toEqual({ size: 20, number: 1, totalElements: 26, totalPages: 2 });
  });

  it('AC-002.2 returns a review due date only for Pending review and Changes requested tickets', async () => {
    const page = await getTickets({ size: 100 });
    for (const t of page.content) {
      const isInReview = t.status === 'PENDING_REVIEW' || t.status === 'CHANGES_REQUESTED';
      expect(t.reviewDueDate !== null).toBe(isInReview);
    }
  });
});
