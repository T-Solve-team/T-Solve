import { invokeRemote } from '@forge/bridge';
import {
  ASK_DEADLINE_MS,
  ASK_POLL_MS,
  ASK_TIMEOUT_MS,
  askQuestion,
  getAccountLink,
  getEligibility,
  rateAnswer,
  startAccountLink,
} from './tsolveClient';

jest.mock('@forge/bridge', () => ({ invokeRemote: jest.fn() }));

const ANSWER = { answerId: 'a-1', outcome: 'ANSWERED', text: 'Restart the VPN [1]', citations: [] };

const turn = (n) => ({ question: `q${n}`, answer: `a${n}` });

const respond = (status, body) => invokeRemote.mockResolvedValue({ status, headers: {}, body });
const startJob = (status = 202, jobId = 'job-1') => {
  respond(200, { status: 'DONE', answer: ANSWER });
  invokeRemote.mockResolvedValueOnce({ status, body: { jobId } });
};
const askNow = (question, history) => askQuestion(question, history, false, { sleep: async () => {} });

describe('tsolveClient', () => {
  beforeEach(() => {
    invokeRemote.mockReset();
    jest.useRealTimers();
  });

  afterEach(() => jest.useRealTimers());

  it('AC-010.3 sends the question and the earlier turns to the questions endpoint', async () => {
    startJob();

    await askNow('Still failing', [turn(1)]);

    expect(invokeRemote).toHaveBeenCalledWith({
      path: '/api/v1/jira-app/questions',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: { question: 'Still failing', history: [turn(1)] },
    });
  });

  it('AC-010.3 sends at most the last 10 earlier turns', async () => {
    startJob();
    const twelve = Array.from({ length: 12 }, (_, i) => turn(i + 1));

    await askNow('q13', twelve);

    expect(invokeRemote.mock.calls[0][0].body.history).toEqual(twelve.slice(2));
  });

  it('AC-010.3 sends an empty history for the first question', async () => {
    startJob();

    await askNow('VPN fails');

    expect(invokeRemote.mock.calls[0][0].body.history).toEqual([]);
  });

  it('AC-010.4 returns the chatbot answer', async () => {
    startJob();

    await expect(askNow('VPN fails')).resolves.toEqual(ANSWER);
  });

  it('AC-010.3 polls every 1.5 seconds until DONE and trims history to 10 (BUG-343)', async () => {
    jest.useFakeTimers();
    const times = [];
    const started = Date.now();
    const twelve = Array.from({ length: 12 }, (_, i) => turn(i + 1));
    invokeRemote.mockImplementation(({ method }) => {
      if (method === 'POST') {
        return Promise.resolve({ status: 202, body: { jobId: 'job/1 ?' } });
      }
      times.push(Date.now() - started);
      return Promise.resolve({
        status: 200,
        body: times.length < 3 ? { status: 'PENDING' } : { status: 'DONE', answer: ANSWER },
      });
    });
    const answer = askQuestion('q13', twelve, false);

    await jest.advanceTimersByTimeAsync(1499);
    expect(invokeRemote).toHaveBeenCalledTimes(1);
    await jest.advanceTimersByTimeAsync(1);
    expect(invokeRemote).toHaveBeenCalledTimes(2);
    await jest.advanceTimersByTimeAsync(3000);

    await expect(answer).resolves.toEqual(ANSWER);
    expect(ASK_POLL_MS).toBe(1500);
    expect(times).toEqual([1500, 3000, 4500]);
    expect(invokeRemote.mock.calls[0][0].body.history).toEqual(twelve.slice(2));
    expect(invokeRemote.mock.calls[1][0]).toEqual({ path: '/api/v1/jira-app/questions/job%2F1%20%3F', method: 'GET' });
    expect(jest.getTimerCount()).toBe(0);
  });

  it.each([200, 201, 202])('AC-010.3 accepts a %s POST with a jobId', async (status) => {
    startJob(status);
    await expect(askNow('VPN fails')).resolves.toEqual(ANSWER);
  });

  it.each([{}, { jobId: '' }, { jobId: 42 }])('AC-010.8 rejects a POST without a usable jobId: %j', async (body) => {
    respond(202, body);
    await expect(askNow('VPN fails')).rejects.toMatchObject({ code: 'MSG06' });
    expect(invokeRemote).toHaveBeenCalledTimes(1);
  });

  it('AC-010.8 FAILED gives MSG06 and stops polling', async () => {
    startJob();
    respond(200, { status: 'FAILED' });
    await expect(askNow('VPN fails')).rejects.toMatchObject({ code: 'MSG06' });
    expect(invokeRemote).toHaveBeenCalledTimes(2);
  });

  it('AC-010.4 a FAILED job keeps the refusal code of the chatbot (MSG08)', async () => {
    startJob();
    respond(200, { status: 'FAILED', code: 'MSG08' });
    await expect(askNow('VPN fails')).rejects.toMatchObject({ code: 'MSG08' });
    expect(invokeRemote).toHaveBeenCalledTimes(2);
  });

  it.each([404, 503])('AC-010.8 AC-010.16 GET %s gives MSG06 and stops polling', async (status) => {
    startJob();
    respond(status, { code: 'MSG06' });
    await expect(askNow('VPN fails')).rejects.toMatchObject({ code: 'MSG06', status });
    expect(invokeRemote).toHaveBeenCalledTimes(2);
  });

  it.each([
    ['POST', 404, 'MSG04'],
    ['GET', 404, 'MSG04'],
    ['POST', 503, 'MSG68'],
    ['GET', 503, 'MSG68'],
  ])('AC-010.8 maps %s %s to MSG06 even with another known code %s', async (method, status, code) => {
    if (method === 'GET') startJob();
    respond(status, { code });
    await expect(askNow('VPN fails')).rejects.toMatchObject({ code: 'MSG06', status });
    expect(invokeRemote).toHaveBeenCalledTimes(method === 'GET' ? 2 : 1);
  });

  it('AC-010.8 a failed GET invocation gives MSG06', async () => {
    startJob();
    invokeRemote.mockRejectedValue(new Error('Network unavailable'));
    await expect(askNow('VPN fails')).rejects.toMatchObject({ code: 'MSG06' });
    expect(invokeRemote).toHaveBeenCalledTimes(2);
  });

  it.each([[403, 'MSG08'], [401, 'MSG124']])(
    'AC-010.8 maps a mid-poll GET %s %s to MSG06 (Task 25 review finding 1)',
    async (status, code) => {
      startJob();
      respond(status, { code });
      await expect(askNow('VPN fails')).rejects.toMatchObject({ code: 'MSG06', status });
      expect(invokeRemote).toHaveBeenCalledTimes(2);
    },
  );

  it('AC-010.8 a GET that never answers gives MSG06 after 20 seconds', async () => {
    jest.useFakeTimers();
    startJob();
    invokeRemote.mockReturnValue(new Promise(() => {}));
    const answer = expect(askQuestion('VPN fails', [], false)).rejects.toMatchObject({ code: 'MSG06' });
    await jest.advanceTimersByTimeAsync(1500 + 19999);
    expect(invokeRemote).toHaveBeenCalledTimes(2);
    await jest.advanceTimersByTimeAsync(1);
    await answer;
    await jest.advanceTimersByTimeAsync(100000);
    expect(invokeRemote).toHaveBeenCalledTimes(2);
    expect(jest.getTimerCount()).toBe(0);
  });

  it('AC-010.8 PENDING at 75 seconds gives MSG06 with no GET at or after the deadline', async () => {
    jest.useFakeTimers();
    startJob();
    respond(200, { status: 'PENDING' });
    const answer = expect(askQuestion('VPN fails', [], false)).rejects.toMatchObject({ code: 'MSG06' });
    await jest.advanceTimersByTimeAsync(75000);
    await answer;
    expect(ASK_DEADLINE_MS).toBe(75000);
    expect(invokeRemote).toHaveBeenCalledTimes(50); // POST and 49 polls through 73.5 seconds.
    await jest.advanceTimersByTimeAsync(100000);
    expect(invokeRemote).toHaveBeenCalledTimes(50);
    expect(jest.getTimerCount()).toBe(0);
  });

  it('AC-010.8 the deadline includes POST time and bounds an in-flight GET', async () => {
    jest.useFakeTimers();
    const started = Date.now();
    invokeRemote.mockImplementation(({ method }) => {
      if (method === 'POST') {
        return new Promise((resolve) => setTimeout(() => resolve({ status: 202, body: { jobId: 'job-1' } }), 19000));
      }
      return Date.now() - started < 70000
        ? Promise.resolve({ status: 200, body: { status: 'PENDING' } })
        : new Promise(() => {});
    });
    const answer = expect(askQuestion('VPN fails', [], false)).rejects.toMatchObject({ code: 'MSG06' });
    await jest.advanceTimersByTimeAsync(75000);
    await answer;
    const calls = invokeRemote.mock.calls.length;
    await jest.advanceTimersByTimeAsync(30000);
    expect(invokeRemote).toHaveBeenCalledTimes(calls);
    expect(jest.getTimerCount()).toBe(0);
  });

  it('AC-010.8 uses injectable sleep and now for the question deadline', async () => {
    let time = 0;
    const sleep = jest.fn(async (ms) => {
      time += ms;
    });
    startJob();
    respond(200, { status: 'PENDING' });
    await expect(askQuestion('VPN fails', [], false, { sleep, now: () => time })).rejects.toMatchObject({
      code: 'MSG06',
    });
    expect(time).toBe(75000);
    expect(sleep).toHaveBeenCalledWith(1500, undefined);
    expect(invokeRemote).toHaveBeenCalledTimes(50);
  });

  it.each(['before POST', 'during POST', 'between polls', 'during GET'])(
    'AC-010.12 abort stops polling %s',
    async (stage) => {
      jest.useFakeTimers();
      const controller = new AbortController();
      startJob();
      respond(200, { status: 'PENDING' });
      if (stage === 'before POST') controller.abort();
      if (stage === 'during POST') invokeRemote.mockImplementationOnce(() => new Promise(() => {}));
      if (stage === 'during GET') invokeRemote.mockReturnValue(new Promise(() => {}));
      const answer = expect(askQuestion('VPN fails', [], false, { signal: controller.signal })).rejects.toMatchObject({
        name: 'AbortError',
      });
      await jest.advanceTimersByTimeAsync(stage === 'during GET' ? 1500 : 0);
      const calls = invokeRemote.mock.calls.length;
      controller.abort();
      await answer;
      await jest.advanceTimersByTimeAsync(100000);
      expect(invokeRemote).toHaveBeenCalledTimes(calls);
      expect(jest.getTimerCount()).toBe(0);
    },
  );

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
    startJob();
    invokeRemote.mockReturnValue(new Promise((resolve) => (deliver = resolve)));

    const answer = askQuestion('VPN fails');
    await jest.advanceTimersByTimeAsync(1500 + ASK_TIMEOUT_MS - 1);
    deliver({ status: 200, headers: {}, body: { status: 'DONE', answer: ANSWER } });

    await expect(answer).resolves.toEqual(ANSWER);
  });

  it('AC-014.2 reads the account link status', async () => {
    respond(200, { status: 'LINKED', fullName: 'An', email: 'an@acme.example', linkedAt: '2026-11-17T08:00:00Z' });

    await expect(getAccountLink()).resolves.toMatchObject({ status: 'LINKED', fullName: 'An' });
    expect(invokeRemote).toHaveBeenCalledWith({ path: '/api/v1/jira-app/account-link', method: 'GET' });
  });

  it('AC-014.2 treats an unknown status as not linked', async () => {
    respond(200, {});

    await expect(getAccountLink()).resolves.toMatchObject({ status: 'NOT_LINKED' });
  });

  it('AC-014.6 starts a link with no body and gives back its URL', async () => {
    respond(200, { url: 'https://acme.tsolve.io.vn/jira-link?request=abc' });

    await expect(startAccountLink()).resolves.toBe('https://acme.tsolve.io.vn/jira-link?request=abc');
    expect(invokeRemote).toHaveBeenCalledWith({ path: '/api/v1/jira-app/account-link', method: 'POST' });
  });

  it('AC-014.6 refuses an answer that is not a web link', async () => {
    respond(200, { url: 'javascript:alert(1)' });

    await expect(startAccountLink()).rejects.toMatchObject({ code: 'MSG06' });
  });

  it.each([
    [403, 'MSG124'],
    [403, 'MSG08'],
    [503, 'MSG06'],
  ])('AC-014.9 gives the %s %s of a refused link', async (status, code) => {
    respond(status, { status, code, params: {} });

    await expect(startAccountLink()).rejects.toMatchObject({ code });
  });

  it('AC-010.12 logs no question or answer text', async () => {
    const spies = ['log', 'info', 'warn', 'error', 'debug'].map((level) =>
      jest.spyOn(console, level).mockImplementation(() => {}),
    );
    startJob();
    await askNow('secret VPN password question', [turn(1)]);
    invokeRemote.mockRejectedValue(new Error('boom'));
    await askQuestion('secret VPN password question').catch(() => {});

    spies.forEach((spy) => {
      expect(spy).not.toHaveBeenCalled();
      spy.mockRestore();
    });
  });
});
