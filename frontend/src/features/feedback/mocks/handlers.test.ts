import { giveFeedback, isApiError, type ApiError, type TicketFeedbackRequest } from '@/shared/api';

/** The first Published ticket of the search mock (features/search/mocks/search-data.ts). */
const PUBLISHED_ID = '5ea70000-0000-4000-8000-000000000001';

async function errorOf(ticketId: string, body: TicketFeedbackRequest): Promise<ApiError> {
  try {
    await giveFeedback(ticketId, body);
  } catch (error) {
    if (isApiError(error)) return error;
    throw error;
  }
  throw new Error('expected the request to fail');
}

describe('feedback mock handlers', () => {
  it('AC-004.4 answers 204 for a valid body, also when it replaces an earlier Feedback', async () => {
    await expect(giveFeedback(PUBLISHED_ID, { isHelpful: true })).resolves.toBeUndefined();
    await expect(
      giveFeedback(PUBLISHED_ID, { isHelpful: false, comment: 'x'.repeat(500) }),
    ).resolves.toBeUndefined();
  });

  it('AC-004.4 answers 400 with MSG01 on isHelpful when it is missing', async () => {
    const error = await errorOf(PUBLISHED_ID, { comment: 'Hi' } as TicketFeedbackRequest);
    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual([
      { field: 'isHelpful', code: 'MSG01', params: { field_name: 'Helpful?' } },
    ]);
  });

  it('AC-004.4 answers 400 with MSG02 on comment when it is longer than 500 characters', async () => {
    const error = await errorOf(PUBLISHED_ID, { isHelpful: true, comment: 'x'.repeat(501) });
    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual([
      { field: 'comment', code: 'MSG02', params: { field_name: 'Comment', max_length: 500 } },
    ]);
  });

  it('AC-004.4 answers 404 for an unknown or not Published ticket', async () => {
    const error = await errorOf('00000000-0000-4000-8000-000000000999', { isHelpful: true });
    expect(error.status).toBe(404);
  });
});
