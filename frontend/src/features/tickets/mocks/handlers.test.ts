import { getTickets, TicketSource, TicketStatus, type GetTicketsParams } from '@/shared/api';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
/** The fixture users' Departments (src/mocks/session.ts). */
const IT_ID = '00000000-0000-4000-8000-0000000000d1';
const HR_ID = '00000000-0000-4000-8000-0000000000d2';

async function ids(params: GetTicketsParams): Promise<string[]> {
  const page = await getTickets({ size: 100, ...params });
  return page.content.map((t) => t.sourceTicketId);
}

describe('tickets mock handlers', () => {
  it('AC-004.1 returns at least 25 tickets with every status, every source, two Departments and UUID ids', async () => {
    const page = await getTickets({ size: 100 });
    const tickets = page.content;
    expect(page.page.totalElements).toBeGreaterThanOrEqual(25);
    expect(new Set(tickets.map((t) => t.status))).toEqual(new Set(Object.values(TicketStatus)));
    expect(new Set(tickets.map((t) => t.source))).toEqual(new Set(Object.values(TicketSource)));
    expect(new Set(tickets.map((t) => t.department.departmentId))).toEqual(new Set([IT_ID, HR_ID]));
    for (const t of tickets) expect(t.ticketId).toMatch(UUID);
    expect(new Set(tickets.map((t) => t.ticketId)).size).toBe(tickets.length);
    expect(tickets[0]).not.toHaveProperty('solution');
  });

  it('AC-004.2 matches the keyword on Ticket ID, title and Solution text, in any case', async () => {
    expect(await ids({ keyword: 'itsup-1031' })).toEqual(['ITSUP-1031']);
    expect(await ids({ keyword: 'PAYSLIP' })).toEqual(['HRHELP-0318']);
    expect(await ids({ keyword: 'split tunnelling' })).toEqual(['ITSUP-1102']); // only in the Solution
  });

  it('AC-004.2 filters by Department, source and status together', async () => {
    expect(
      await ids({ departmentId: HR_ID, source: 'SOLUTION_FORM', status: 'PUBLISHED' }),
    ).toEqual(['HRHELP-0232']);
  });

  it('AC-004.2 sorts by updatedAt newest first by default and by any sort key', async () => {
    const byDefault = await getTickets({ size: 100 });
    const updated = byDefault.content.map((t) => t.updatedAt);
    expect(updated).toEqual([...updated].sort().reverse());
    expect((await ids({ sort: ['sourceTicketId,asc'] }))[0]).toBe('HRHELP-0228');
    expect((await ids({ sort: ['sourceTicketId,desc'] }))[0]).toBe('ITSUP-1102');
  });

  it('AC-004.2 pages the result', async () => {
    const second = await getTickets({ page: 1, size: 20 });
    expect(second.content).toHaveLength(8);
    expect(second.page).toEqual({ size: 20, number: 1, totalElements: 28, totalPages: 2 });
  });

  it('AC-004.2 returns a review due date only for Pending review and Changes requested tickets', async () => {
    const page = await getTickets({ size: 100 });
    for (const t of page.content) {
      const isInReview = t.status === 'PENDING_REVIEW' || t.status === 'CHANGES_REQUESTED';
      expect(t.reviewDueDate !== null).toBe(isInReview);
    }
  });
});
