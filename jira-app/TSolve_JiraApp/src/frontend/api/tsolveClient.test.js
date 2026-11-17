import { invokeRemote } from '@forge/bridge';
import { ASK_TIMEOUT_MS, askQuestion, getEligibility, rateAnswer } from './tsolveClient';

jest.mock('@forge/bridge', () => ({ invokeRemote: jest.fn() }));

const ANSWER = { answerId: 'a-1', outcome: 'ANSWERED', text: 'Restart the VPN [1]', citations: [] };

const turn = (n) => ({ question: `q${n}`, answer: `a${n}` });

const respond = (status, body) => invokeRemote.mockResolvedValue({ status, headers: {}, body });

describe('tsolveClient', () => {
  beforeEach(() => {
    invokeRemote.mockReset();
    jest.useRealTimers();
  });

  it('AC-010.3 sends the question and the earlier turns to the questions endpoint', async () => {
    respond(200, ANSWER);

    await askQuestion('Still failing', [turn(1)]);

    expect(invokeRemote).toHaveBeenCalledWith({
      path: '/api/v1/jira-app/questions',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: { question: 'Still failing', history: [turn(1)] },
    });
  });

  it('AC-010.3 sends at most the last 10 earlier turns', async () => {
    respond(200, ANSWER);
    const twelve = Array.from({ length: 12 }, (_, i) => turn(i + 1));

    await askQuestion('q13', twelve);

    expect(invokeRemote.mock.calls[0][0].body.history).toEqual(twelve.slice(2));
  });

  it('AC-010.3 sends an empty history for the first question', async () => {
    respond(200, ANSWER);

    await askQuestion('VPN fails');

    expect(invokeRemote.mock.calls[0][0].body.history).toEqual([]);
  });

  it('AC-010.4 returns the chatbot answer', async () => {
    respond(200, ANSWER);

    await expect(askQuestion('VPN fails')).resolves.toEqual(ANSWER);
  });

  it.each(['CONNECTED', 'SITE_NOT_CONNECTED', 'PROJECT_NOT_CONNECTED'])(
    'AC-010.14 reads the eligibility status %s',
    async (status) => {
      respond(200, { status });

      await expect(getEligibility()).resolves.toEqual({ status });
      expect(invokeRemote).toHaveBeenCalledWith({ path: '/api/v1/jira-app/questions/eligibility', method: 'GET' });
    },
  );

  it('AC-010.14 treats an unknown status as connected (the question is refused anyway)', async () => {
    respond(200, {});

    await expect(getEligibility()).resolves.toEqual({ status: 'CONNECTED' });
  });

  it('AC-010.15 puts the rating of an answer', async () => {
    respond(204, undefined);

    await rateAnswer('a-1', 'HELPFUL', false);

    expect(invokeRemote).toHaveBeenCalledWith({
      path: '/api/v1/jira-app/questions/a-1/rating',
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: { rating: 'HELPFUL' },
    });
  });

  it.each(['MSG01', 'MSG02'])('AC-010.5 gives the server %s of a rejected question', async (code) => {
    respond(400, { status: 400, code, params: { field_name: 'question' } });

    await expect(askQuestion('x')).rejects.toMatchObject({ code });
  });

  it.each(['MSG08', 'MSG124'])('AC-010.11 gives %s when the project or site is not connected', async (code) => {
    respond(403, { status: 403, code, params: {} });

    await expect(askQuestion('VPN fails')).rejects.toMatchObject({ code });
  });

  it.each([500, 503])('AC-010.8 gives MSG06 for a %s answer', async (status) => {
    respond(status, { status, code: 'MSG06', params: {} });

    await expect(askQuestion('VPN fails')).rejects.toMatchObject({ code: 'MSG06' });
  });

  it('AC-010.8 gives MSG06 when the call itself fails', async () => {
    invokeRemote.mockRejectedValue(new Error('Remote invocation timed out'));

    await expect(askQuestion('VPN fails')).rejects.toMatchObject({ code: 'MSG06' });
  });

  it('AC-010.8 gives MSG06 when there is no answer within 20 seconds', async () => {
    jest.useFakeTimers();
    invokeRemote.mockReturnValue(new Promise(() => {}));

    const answer = expect(askQuestion('VPN fails')).rejects.toMatchObject({ code: 'MSG06' });
    jest.advanceTimersByTime(ASK_TIMEOUT_MS);

    await answer;
    expect(ASK_TIMEOUT_MS).toBe(20000);
  });

  it('AC-010.8 waits for an answer that comes just before 20 seconds', async () => {
    jest.useFakeTimers();
    let deliver;
    invokeRemote.mockReturnValue(new Promise((resolve) => (deliver = resolve)));

    const answer = askQuestion('VPN fails');
    jest.advanceTimersByTime(ASK_TIMEOUT_MS - 1);
    deliver({ status: 200, headers: {}, body: ANSWER });

    await expect(answer).resolves.toEqual(ANSWER);
  });

  it('AC-010.12 logs no question or answer text', async () => {
    const spies = ['log', 'info', 'warn', 'error', 'debug'].map((level) =>
      jest.spyOn(console, level).mockImplementation(() => {}),
    );
    respond(200, ANSWER);
    await askQuestion('secret VPN password question', [turn(1)]);
    invokeRemote.mockRejectedValue(new Error('boom'));
    await askQuestion('secret VPN password question').catch(() => {});

    spies.forEach((spy) => {
      expect(spy).not.toHaveBeenCalled();
      spy.mockRestore();
    });
  });
});
