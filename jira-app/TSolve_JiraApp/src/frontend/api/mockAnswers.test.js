import { invokeRemote } from '@forge/bridge';
import { mockAnswer } from './mockAnswers';
import { USE_MOCK } from './mode';
import { askQuestion, getEligibility } from './tsolveClient';

jest.mock('@forge/bridge', () => ({ invokeRemote: jest.fn() }));

describe('mockAnswer', () => {
  it('answers a VPN question with a ticket Solution and a Wiki document', () => {
    const answer = mockAnswer('VPN của mình cứ rớt');

    expect(answer.outcome).toBe('ANSWERED');
    expect(answer.citations.map((c) => c.type)).toEqual(['TICKET_SOLUTION', 'WIKI_DOCUMENT']);
  });

  it('answers a password question with one Wiki document', () => {
    expect(mockAnswer('Quên mật khẩu').citations).toHaveLength(1);
  });

  it('has no source for any other question', () => {
    expect(mockAnswer('Máy in kẹt giấy')).toMatchObject({ outcome: 'NO_SOURCE', citations: [] });
  });

  it('fails for "lỗi mạng"', () => {
    expect(mockAnswer('Thử lỗi mạng')).toBeNull();
  });
});

describe('the client in mock mode', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('is off in the committed code', () => {
    expect(USE_MOCK).toBe(false);
  });

  it('answers from the mock and never calls the backend', async () => {
    const answer = askQuestion('VPN fails', [], true);
    jest.runAllTimers();

    await expect(answer).resolves.toMatchObject({ outcome: 'ANSWERED' });
    await expect(getEligibility(true)).resolves.toEqual({ linked: true });
    expect(invokeRemote).not.toHaveBeenCalled();
  });

  it('fails with MSG06 for "lỗi mạng"', async () => {
    const answer = askQuestion('lỗi mạng', [], true);
    jest.runAllTimers();

    await expect(answer).rejects.toMatchObject({ code: 'MSG06' });
  });
});
