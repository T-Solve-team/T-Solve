import {
  approveTickets,
  getMe,
  getReviewQueue,
  getReviewQueueTicketIds,
  getReviewTicketDetail,
  rejectTickets,
  requestTicketChanges,
  updateUserRoles,
  updateUserStatus,
  getUserHandoverOptions,
} from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { DETAIL_TICKET_IDS, seedReviewTickets, ticketUuid } from './data';
import { resetMockData } from './handlers';
import { reviewStore } from './state';

describe('review mock contract', () => {
  it('AC-005.1 returns UUID Ticket and Project ids and accepts a UUID decision (BUG-080)', async () => {
    const page = await getReviewQueue();
    const ticket = page.content[0];
    expect(ticket).toBeDefined();
    expect(ticket?.ticketId).toEqual(expect.stringMatching(/^[0-9a-f-]{36}$/));
    expect(ticket?.project.projectId).toEqual(expect.stringMatching(/^[0-9a-f-]{36}$/));
    if (!ticket) throw new Error('Expected seeded Ticket');
    expect(await approveTickets({ ticketIds: [ticket.ticketId] })).toEqual({ count: 1 });
  });

  it('AC-001.5 rejects direct signed-out and Employee queue/id/decision calls (BUG-081)', async () => {
    const ids = await getReviewQueueTicketIds();
    for (const [actor, status] of [
      [null, 401],
      ['employee', 403],
    ] as const) {
      setDevUser(actor);
      await expect(getReviewQueue()).rejects.toMatchObject({ status });
      await expect(getReviewQueueTicketIds()).rejects.toMatchObject({ status });
      await expect(approveTickets({ ticketIds: ids.ticketIds.slice(0, 1) })).rejects.toMatchObject({
        status,
      });
    }
  });

  it('AC-005.3 rejects an entire stale batch without deciding its remaining pending Ticket (BUG-081)', async () => {
    const ids = (await getReviewQueueTicketIds()).ticketIds.slice(0, 2);
    const first = ids[0];
    if (first === undefined) throw new Error('Expected seeded Ticket');
    await approveTickets({ ticketIds: [first] });
    await expect(approveTickets({ ticketIds: ids })).rejects.toMatchObject({ status: 409 });
    expect((await getReviewQueueTicketIds()).ticketIds).toContain(ids[1]);
  });

  it('AC-001.1 returns only scoped Pending review Solution Form tickets with server paging, Department and stable sorting (BUG-081)', async () => {
    const ids = (await getReviewQueueTicketIds()).ticketIds;
    expect(ids).toHaveLength(58);
    expect(new Set(ids).size).toBe(58);
    expect((await getReviewQueue({ page: 1 })).content.map((ticket) => ticket.ticketId)).toEqual(
      ids.slice(20, 40),
    );
    expect(
      (await getReviewQueue({ departmentId: '00000000-0000-4000-8000-0000000000d2' })).content,
    ).toEqual([]);
    expect(
      (await getReviewQueue({ sort: ['sourceTicketId,desc'] })).content[0]?.sourceTicketId,
    ).toBe('ITSUP-1248');
    setDevUser('pm-km');
    expect((await getReviewQueueTicketIds()).ticketIds).toEqual([DETAIL_TICKET_IDS.unmanaged]);
  });

  it.each([
    ['missing', ticketUuid(999), 404],
    ['foreign Workspace', DETAIL_TICKET_IDS.foreign, 404],
    ['unmanaged Project', DETAIL_TICKET_IDS.unmanaged, 403],
    ['Published', DETAIL_TICKET_IDS.published, 409],
    ['Rejected', DETAIL_TICKET_IDS.rejected, 409],
    ['Changes requested', DETAIL_TICKET_IDS.authored, 409],
  ])(
    'AC-005.3 atomically rejects a mixed valid and %s Ticket batch (BUG-081)',
    async (_label, invalidId, status) => {
      const before = structuredClone(reviewStore);
      await expect(approveTickets({ ticketIds: [ticketUuid(1), invalidId] })).rejects.toMatchObject(
        { status },
      );
      expect(reviewStore).toEqual(before);
    },
  );

  it.each([
    [],
    [ticketUuid(1), ticketUuid(1)],
    ['bad-id'],
    Array.from({ length: 501 }, (_, i) => ticketUuid(i + 1)),
  ])(
    'AC-005.2 rejects invalid, empty, duplicate or oversized ids %# without writes (BUG-081)',
    async (...ids) => {
      const before = structuredClone(reviewStore);
      await expect(approveTickets({ ticketIds: ids })).rejects.toMatchObject({ status: 400 });
      expect(reviewStore).toEqual(before);
    },
  );

  it.each([
    ['AC-003.2', 'reason'],
    ['AC-004.2', 'comment'],
  ] as const)(
    '%s validates required and raw 1000-character %s, retaining valid text (BUG-081)',
    async (_criterion, field) => {
      const send = (text: string) =>
        field === 'reason'
          ? rejectTickets({ ticketIds: [ticketUuid(1)], reason: text })
          : requestTicketChanges({ ticketIds: [ticketUuid(1)], comment: text });
      const before = structuredClone(reviewStore);
      await expect(send(' \n\t ')).rejects.toMatchObject({
        status: 400,
        code: 'MSG01',
        fieldErrors: [{ field, code: 'MSG01' }],
      });
      await expect(send('x'.repeat(1001))).rejects.toMatchObject({
        status: 400,
        code: 'MSG02',
        fieldErrors: [{ field, params: { max_length: 1000 } }],
      });
      expect(reviewStore).toEqual(before);
      const valid = ` ${'x'.repeat(998)} `;
      await send(valid);
      expect(reviewStore.history[0]?.[field]).toBe(valid);
    },
  );

  it.each([
    ['AC-002.1', 'APPROVE', 'PUBLISHED'],
    ['AC-003.3', 'REJECT', 'REJECTED'],
    ['AC-004.3', 'REQUEST_CHANGES', 'CHANGES_REQUESTED'],
  ] as const)(
    '%s AC-005.7 preserves immutable fields and appends exactly one %s history entry per Ticket (BUG-081)',
    async (_criterion, action, status) => {
      const ids = [ticketUuid(1), ticketUuid(2)];
      const before = structuredClone(
        reviewStore.tickets.filter((ticket) => ids.includes(ticket.ticketId)),
      );
      const user = await getMe();
      const send = () =>
        action === 'APPROVE'
          ? approveTickets({ ticketIds: ids })
          : action === 'REJECT'
            ? rejectTickets({ ticketIds: ids, reason: '<b>reason</b>' })
            : requestTicketChanges({ ticketIds: ids, comment: '<b>comment</b>' });
      expect(await send()).toEqual({ count: 2 });
      expect(reviewStore.history).toHaveLength(2);
      for (const original of before) {
        const current = reviewStore.tickets.find((ticket) => ticket.ticketId === original.ticketId);
        expect(current).toEqual({
          ...original,
          status,
          lastReviewComment:
            action === 'REQUEST_CHANGES' ? '<b>comment</b>' : original.lastReviewComment,
        });
        const entry = reviewStore.history.find((entry) => entry.ticketId === original.ticketId);
        expect(entry).toMatchObject({
          ticketId: original.ticketId,
          actorId: user.user.id,
          action,
          beforeStatus: 'PENDING_REVIEW',
          afterStatus: status,
          reason: action === 'REJECT' ? '<b>reason</b>' : null,
          comment: action === 'REQUEST_CHANGES' ? '<b>comment</b>' : null,
        });
        expect(Number.isNaN(Date.parse(entry?.timestamp ?? ''))).toBe(false);
        expect((await getReviewTicketDetail(original.ticketId)).status).toBe(status);
      }
      const remainingIds = (await getReviewQueueTicketIds()).ticketIds;
      for (const id of ids) expect(remainingIds).not.toContain(id);
      const after = structuredClone(reviewStore);
      await expect(send()).rejects.toMatchObject({ status: 409 });
      expect(reviewStore).toEqual(after);
    },
  );

  it('AC-004.4 rejects Request Changes on an imported Published Ticket with MSG18 before stale-state validation (BUG-081)', async () => {
    const before = structuredClone(reviewStore);
    await expect(
      requestTicketChanges({
        ticketIds: [ticketUuid(1), DETAIL_TICKET_IDS.imported],
        comment: 'More information',
      }),
    ).rejects.toMatchObject({ status: 400, code: 'MSG18' });
    expect(reviewStore).toEqual(before);
  });

  it('AC-002.3 AC-006.6 allows manager self-approval and independently checks author UUID for unpublished detail (BUG-081)', async () => {
    const self = reviewStore.tickets.find((ticket) => ticket.ticketId === ticketUuid(3));
    if (!self) throw new Error('Expected self-authored fixture');
    self.authorId = (await getMe()).user.id;
    await approveTickets({ ticketIds: [self.ticketId] });
    setDevUser('tr');
    expect((await getReviewTicketDetail(DETAIL_TICKET_IDS.authored)).status).toBe(
      'CHANGES_REQUESTED',
    );
    await expect(getReviewTicketDetail(ticketUuid(1))).rejects.toMatchObject({
      status: 404,
      code: 'MSG04',
    });
    const forged = reviewStore.tickets.find((ticket) => ticket.ticketId === ticketUuid(1));
    if (!forged) throw new Error('Expected fixture');
    forged.authorName = (await getMe()).user.fullName;
    await expect(getReviewTicketDetail(forged.ticketId)).rejects.toMatchObject({
      status: 404,
      code: 'MSG04',
    });
    setDevUser('employee');
    expect((await getReviewTicketDetail(DETAIL_TICKET_IDS.published)).status).toBe('PUBLISHED');
    expect((await getReviewTicketDetail(DETAIL_TICKET_IDS.imported)).source).toBe('CSV');
    await expect(getReviewTicketDetail(DETAIL_TICKET_IDS.foreign)).rejects.toMatchObject({
      status: 404,
    });
  });

  it('AC-001.5 resolves current rights after role removal and deactivation through existing APIs (BUG-081)', async () => {
    const before = structuredClone(reviewStore);
    setDevUser('admin');
    const options = await getUserHandoverOptions('10000000-0000-4000-8000-000000000003');
    const spaceHandovers = options.spaces.map(({ space, recipients }) => {
      const recipient = recipients[0];
      if (!recipient) throw new Error('Expected eligible Space recipient');
      return { spaceId: space.spaceId, recipientId: recipient.id };
    });
    await updateUserRoles('10000000-0000-4000-8000-000000000003', {
      roles: ['EMPLOYEE'],
      departmentId: null,
      spaceIds: [],
      spaceHandovers,
    });
    setDevUser('pm');
    await expect(approveTickets({ ticketIds: [ticketUuid(1)] })).rejects.toMatchObject({
      status: 403,
    });
    await expect(getReviewQueueTicketIds()).rejects.toMatchObject({ status: 403 });
    setDevUser('admin');
    await updateUserStatus('10000000-0000-4000-8000-000000000003', { status: 'DEACTIVATED' });
    setDevUser('pm');
    await expect(getReviewQueue()).rejects.toMatchObject({ status: 401 });
    await expect(approveTickets({ ticketIds: [ticketUuid(1)] })).rejects.toMatchObject({
      status: 401,
    });
    expect(reviewStore).toEqual(before);
  });

  it('resets all decisions, content changes and history without retaining session data', async () => {
    await rejectTickets({ ticketIds: [ticketUuid(1)], reason: 'Review reason' });
    resetMockData();
    expect(reviewStore).toEqual({ tickets: seedReviewTickets(), history: [] });
  });

  it.each([
    null,
    {},
    { ticketIds: null },
    { ticketIds: [null] },
    { ticketIds: [1] },
    'not an object',
  ])(
    'AC-005.2 rejects malformed serialized payload %# without writes (BUG-081)',
    async (payload) => {
      const before = structuredClone(reviewStore);
      // Serialize intentionally invalid transport data through the generated operation, without weakening its types.
      const request = { ticketIds: [ticketUuid(1)], toJSON: () => payload };
      await expect(approveTickets(request)).rejects.toMatchObject({ status: 400 });
      expect(reviewStore).toEqual(before);
    },
  );

  it('AC-005.2 accepts exactly 500 unique eligible ids atomically (BUG-081)', async () => {
    const first = reviewStore.tickets[0];
    if (!first) throw new Error('Expected seeded Ticket');
    reviewStore.tickets = Array.from({ length: 500 }, (_, i) => ({
      ...structuredClone(first),
      ticketId: ticketUuid(3000 + i),
    }));
    const ticketIds = reviewStore.tickets.map((ticket) => ticket.ticketId);
    expect(await approveTickets({ ticketIds })).toEqual({ count: 500 });
    expect(reviewStore.history).toHaveLength(500);
    expect(reviewStore.tickets.every((ticket) => ticket.status === 'PUBLISHED')).toBe(true);
  });

  it('AC-003.4 rejects every subsequent decision on a Rejected Ticket without changing the batch or history (BUG-081)', async () => {
    const ticket = reviewStore.tickets[0];
    if (!ticket) throw new Error('Expected seeded Ticket');
    await rejectTickets({ ticketIds: [ticket.ticketId], reason: 'Original review' });
    const afterRejection = structuredClone(reviewStore);
    const ticketIds = [ticket.ticketId, ticketUuid(2)];
    for (const send of [
      () => approveTickets({ ticketIds }),
      () => rejectTickets({ ticketIds, reason: 'Another rejection' }),
      () => requestTicketChanges({ ticketIds, comment: 'Reconsider' }),
    ]) {
      await expect(send()).rejects.toMatchObject({ status: 409 });
      expect(reviewStore).toEqual(afterRejection);
    }
  });
});
