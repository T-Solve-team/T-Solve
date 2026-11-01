import {
  getExpiredTickets,
  getExpiredTicketIds,
  sendBackTickets,
  getReviewQueueTicketIds,
  getReviewTicketDetail,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { MOCK_PROJECTS, ticketUuid, REVIEW_WORKSPACE_ID } from './data';
import { reviewStore } from './state';

function firstExpired() {
  const ticket = reviewStore.tickets.find((t) => t.status === 'EXPIRED');
  if (!ticket) throw new Error('Missing expiry fixture');
  return ticket;
}
describe('Expired Ticket mock contract', () => {
  it('AC-007.1 / AC-007.2 returns only managed expired tickets, filters and sorts before pagination', async () => {
    const all = await getExpiredTicketIds();
    expect(all.ticketIds).toHaveLength(24);
    const page = await getExpiredTickets({ page: 1, size: 5, sort: ['sourceTicketId,asc'] });
    expect(page.content.map((t) => t.sourceTicketId)).toEqual([
      'EXP-305',
      'EXP-306',
      'EXP-307',
      'EXP-308',
      'EXP-309',
    ]);
    expect(page.page.totalElements).toBe(24);
    expect(await getExpiredTicketIds({ departmentId: MOCK_PROJECTS[2].departmentId })).toEqual({
      ticketIds: [],
    });
    const ticket = firstExpired();
    ticket.project = { projectId: MOCK_PROJECTS[2].projectId, name: MOCK_PROJECTS[2].name };
    expect((await getExpiredTicketIds()).ticketIds).not.toContain(ticket.ticketId);
    ticket.project = { projectId: MOCK_PROJECTS[0].projectId, name: MOCK_PROJECTS[0].name };
    ticket.workspaceId = 'ffffffff-ffff-4fff-8fff-ffffffffffff';
    expect((await getExpiredTicketIds()).ticketIds).not.toContain(ticket.ticketId);
  });
  it('AC-007.4 recalculates due dates by Vietnam calendar and current Project range, preserving content/history', async () => {
    const tickets = reviewStore.tickets.filter((t) => t.status === 'EXPIRED').slice(0, 3);
    const before = structuredClone(tickets);
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-01T18:30:00Z')); // Nov 2 in Vietnam
    try {
      expect(await sendBackTickets({ ticketIds: tickets.map((t) => t.ticketId) })).toEqual({
        count: 3,
      });
      for (const [i, ticket] of tickets.entries()) {
        const days = MOCK_PROJECTS.find(
          (p) => p.projectId === ticket.project.projectId,
        )?.reviewRangeDays;
        const expected = days === 5 ? '2026-11-06T17:00:00.000Z' : '2026-11-04T17:00:00.000Z';
        expect(ticket.reviewDueDate).toBe(expected);
        expect(ticket.status).toBe('PENDING_REVIEW');
        expect(ticket.enteredQueueAt).toBe('2026-11-01T18:30:00.000Z');
        expect(ticket.solution).toEqual(before[i]?.solution);
        expect(ticket.evidence).toEqual(before[i]?.evidence);
        expect(reviewStore.history.find((h) => h.ticketId === ticket.ticketId)).toMatchObject({
          action: 'SEND_BACK',
          beforeStatus: 'EXPIRED',
          afterStatus: 'PENDING_REVIEW',
          actorId: '00000000-0000-4000-8000-000000000003',
        });
        expect((await getReviewQueueTicketIds()).ticketIds).toContain(ticket.ticketId);
        expect((await getReviewTicketDetail(ticket.ticketId)).status).toBe('PENDING_REVIEW');
      }
      expect((await getExpiredTicketIds()).ticketIds).toHaveLength(21);
    } finally {
      vi.useRealTimers();
    }
  });
  it('AC-007.5 rejects malformed and stale batches without partial writes', async () => {
    const ticket = firstExpired();
    const pending = reviewStore.tickets.find((t) => t.status === 'PENDING_REVIEW');
    if (!pending) throw new Error('Missing pending fixture');
    const snapshot = structuredClone(reviewStore);
    for (const ticketIds of [
      [],
      ['bad'],
      [ticket.ticketId, ticket.ticketId],
      Array.from({ length: 501 }, (_, i) => ticketUuid(5000 + i)),
      [ticket.ticketId, pending.ticketId],
      [ticket.ticketId, ticketUuid(99999)],
    ]) {
      await expect(sendBackTickets({ ticketIds })).rejects.toMatchObject({
        status:
          ticketIds.length === 2 && ticketIds[0] !== ticketIds[1]
            ? ticketIds[1] === pending.ticketId
              ? 409
              : 404
            : 400,
      });
      expect(reviewStore).toEqual(snapshot);
    }
  });
  it('AC-007.8 denies signed-out, non-manager, foreign and unmanaged calls without data or writes', async () => {
    const ticket = firstExpired();
    const snapshot = structuredClone(reviewStore);
    for (const [user, status] of [
      [null, 401],
      ['employee', 403],
    ] as const) {
      setDevUser(user);
      await expect(getExpiredTickets()).rejects.toMatchObject({ status });
      await expect(getExpiredTicketIds()).rejects.toMatchObject({ status });
      await expect(sendBackTickets({ ticketIds: [ticket.ticketId] })).rejects.toMatchObject({
        status,
      });
      expect(reviewStore).toEqual(snapshot);
    }
    setDevUser('pm');
    ticket.workspaceId = 'ffffffff-ffff-4fff-8fff-ffffffffffff';
    const foreign = structuredClone(reviewStore);
    await expect(sendBackTickets({ ticketIds: [ticket.ticketId] })).rejects.toMatchObject({
      status: 404,
    });
    expect(reviewStore).toEqual(foreign);
    ticket.workspaceId = REVIEW_WORKSPACE_ID;
    ticket.project = { projectId: MOCK_PROJECTS[2].projectId, name: MOCK_PROJECTS[2].name };
    const unmanaged = structuredClone(reviewStore);
    await expect(sendBackTickets({ ticketIds: [ticket.ticketId] })).rejects.toMatchObject({
      status: 403,
    });
    expect(reviewStore).toEqual(unmanaged);
  });
});
