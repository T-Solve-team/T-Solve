import {
  approveTickets,
  rejectTickets,
  requestTicketChanges,
  sendBackTickets,
  getReviewHistory,
  getMe,
  ReviewDecision,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { DETAIL_TICKET_IDS, MOCK_PROJECTS, ticketUuid } from './data';
import { reviewStore } from './state';

function seed() {
  Object.values(ReviewDecision).forEach((action, index) =>
    reviewStore.history.push({
      decisionId: ticketUuid(900 + index),
      ticketId: ticketUuid(1),
      actorId: ticketUuid(800),
      actorName: 'Historical Manager',
      action,
      timestamp: `2026-11-0${index + 1}T12:00:00Z`,
      beforeStatus: 'PENDING_REVIEW',
      afterStatus: 'PUBLISHED',
      reason: action === 'REJECT' ? '<script>reason</script>' : null,
      comment: action === 'REQUEST_CHANGES' ? 'Please clarify' : null,
    }),
  );
}
describe('Review History mock', () => {
  it('AC-008.1 / AC-008.2 / AC-008.4 scopes, sorts and paginates distinct decisions before counting', async () => {
    seed();
    const first = reviewStore.history[0];
    if (!first) throw new Error('Missing history');
    reviewStore.history.push(
      { ...first, decisionId: ticketUuid(990), ticketId: DETAIL_TICKET_IDS.foreign },
      { ...first, decisionId: ticketUuid(991), ticketId: DETAIL_TICKET_IDS.unmanaged },
    );
    const snapshot = structuredClone(reviewStore);
    const page = await getReviewHistory({ page: 1, size: 2 });
    expect(page.page.totalElements).toBe(6);
    expect(page.content.map((e) => e.decision)).toEqual(['UNPUBLISH', 'REQUEST_CHANGES']);
    expect((await getReviewHistory({ decision: 'REJECT' })).content[0]?.reason).toBe(
      '<script>reason</script>',
    );
    expect(
      (await getReviewHistory({ sort: ['timestamp,asc'], size: 100 })).content.map(
        (e) => e.decision,
      ),
    ).toEqual(Object.values(ReviewDecision));
    expect(
      (await getReviewHistory({ departmentId: MOCK_PROJECTS[2].departmentId })).page.totalElements,
    ).toBe(0);
    expect(reviewStore).toEqual(snapshot);
  });
  it('AC-008.1 orders offset timestamps by instant with deterministic UUID tie-breaks', async () => {
    seed();
    const first = reviewStore.history[0];
    if (!first) throw new Error('Missing history');
    reviewStore.history.length = 0;
    reviewStore.history.push(
      { ...first, decisionId: ticketUuid(952), timestamp: '2026-11-01T03:00:00+07:00' },
      { ...first, decisionId: ticketUuid(951), timestamp: '2026-10-31T22:00:00Z' },
      { ...first, decisionId: ticketUuid(950), timestamp: '2026-11-01T05:00:00+07:00' },
    );
    expect((await getReviewHistory()).content.map((entry) => entry.decisionId)).toEqual([
      ticketUuid(950),
      ticketUuid(951),
      ticketUuid(952),
    ]);
  });
  it('AC-008.3 includes both Vietnam calendar boundaries and rejects invalid paired ranges without writes', async () => {
    seed();
    const first = reviewStore.history[0];
    if (!first) throw new Error('Missing history');
    reviewStore.history.length = 0;
    [
      '2026-10-31T16:59:59.999Z',
      '2026-10-31T17:00:00Z',
      '2026-11-01T16:59:59.999Z',
      '2026-11-01T17:00:00Z',
    ].forEach((timestamp, index) =>
      reviewStore.history.push({ ...first, decisionId: ticketUuid(950 + index), timestamp }),
    );
    expect(
      (await getReviewHistory({ dateFrom: '2026-11-01', dateTo: '2026-11-01' })).content.map(
        (e) => e.decisionId,
      ),
    ).toEqual([ticketUuid(952), ticketUuid(951)]);
    const snapshot = structuredClone(reviewStore);
    for (const range of [
      { dateFrom: '2026-11-01' },
      { dateFrom: '2026-02-30', dateTo: '2026-03-01' },
      { dateFrom: '2026-11-02', dateTo: '2026-11-01' },
      { dateFrom: 'invalid', dateTo: '2026-11-01' },
    ]) {
      await expect(getReviewHistory(range)).rejects.toMatchObject({ status: 400, code: 'MSG39' });
      expect(reviewStore).toEqual(snapshot);
    }
    expect(
      (await getReviewHistory({ dateFrom: '2099-01-01', dateTo: '2099-01-01' })).page.totalElements,
    ).toBe(0);
  });
  it('AC-008.5 snapshots UUID/actor and content for all existing producers, with no record on failure', async () => {
    const user = await getMe();
    await approveTickets({ ticketIds: [ticketUuid(1)] });
    await rejectTickets({ ticketIds: [ticketUuid(2)], reason: 'Exact reason' });
    await requestTicketChanges({ ticketIds: [ticketUuid(3)], comment: 'Exact comment' });
    await sendBackTickets({ ticketIds: [ticketUuid(300)] });
    const page = await getReviewHistory();
    expect(page.content).toHaveLength(4);
    expect(new Set(page.content.map((e) => e.decisionId)).size).toBe(4);
    for (const entry of page.content) {
      expect(entry.decisionId).toMatch(/^[0-9a-f-]{36}$/);
      expect(entry.actorId).toBe(user.user.id);
      expect(entry.actorName).toBe(user.user.fullName);
    }
    expect(page.content.find((e) => e.decision === 'REJECT')?.reason).toBe('Exact reason');
    expect(page.content.find((e) => e.decision === 'REQUEST_CHANGES')?.comment).toBe(
      'Exact comment',
    );
    const before = structuredClone(reviewStore.history);
    await expect(approveTickets({ ticketIds: [ticketUuid(2)] })).rejects.toMatchObject({
      status: 409,
    });
    await expect(
      rejectTickets({ ticketIds: [ticketUuid(2)], reason: 'Again' }),
    ).rejects.toMatchObject({ status: 409 });
    await expect(
      requestTicketChanges({ ticketIds: [ticketUuid(2)], comment: 'Again' }),
    ).rejects.toMatchObject({ status: 409 });
    await expect(sendBackTickets({ ticketIds: [ticketUuid(2)] })).rejects.toMatchObject({
      status: 409,
    });
    expect(reviewStore.history).toEqual(before);
  });
  it('AC-008.7 denies signed-out/non-PM and never broadens foreign Department filters', async () => {
    seed();
    for (const [actor, status] of [
      [null, 401],
      ['employee', 403],
    ] as const) {
      setDevUser(actor);
      await expect(getReviewHistory()).rejects.toMatchObject({ status });
    }
    setDevUser('pm');
    expect(
      (await getReviewHistory({ departmentId: 'ffffffff-ffff-4fff-8fff-ffffffffffff' })).content,
    ).toEqual([]);
  });
});
