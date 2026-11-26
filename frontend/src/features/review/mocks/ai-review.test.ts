import { runAiReview } from '@/shared/api';
import { setDevUser } from '@/shared/auth';
import { AI_REVIEW_FINDINGS, DETAIL_TICKET_IDS, ticketUuid } from './data';
import { reviewStore } from './state';

describe('AI check mock contract', () => {
  it('AC-015.4 returns the advisory findings HIGH first for a managed Pending review ticket', async () => {
    const result = await runAiReview(ticketUuid(1));
    expect(result.findings).toEqual(AI_REVIEW_FINDINGS);
    const order = ['HIGH', 'MEDIUM', 'LOW'];
    const ranks = result.findings.map((finding) => order.indexOf(finding.severity));
    expect(ranks).toEqual([...ranks].sort((a, b) => a - b));
    expect(result.findings.length).toBeLessThanOrEqual(10);
  });

  it('AC-015.7 refuses signed-out, non-manager, unmanaged, foreign and not pending tickets', async () => {
    const cases = [
      [DETAIL_TICKET_IDS.unmanaged, 403],
      [DETAIL_TICKET_IDS.foreign, 404],
      [ticketUuid(999), 404],
      [DETAIL_TICKET_IDS.published, 409],
      [DETAIL_TICKET_IDS.rejected, 409],
    ] as const;
    for (const [ticketId, status] of cases) {
      await expect(runAiReview(ticketId)).rejects.toMatchObject({ status });
    }
    setDevUser('employee');
    await expect(runAiReview(ticketUuid(1))).rejects.toMatchObject({ status: 403 });
    setDevUser(null);
    await expect(runAiReview(ticketUuid(1))).rejects.toMatchObject({ status: 401 });
  });

  it('AC-015.8 stores nothing and records no review decision', async () => {
    const before = structuredClone(reviewStore);
    await runAiReview(ticketUuid(1));
    expect(reviewStore).toEqual(before);
  });
});
