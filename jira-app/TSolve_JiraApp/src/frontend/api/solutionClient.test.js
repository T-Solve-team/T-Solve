import { invokeRemote } from '@forge/bridge';
import {
  ASK_DEADLINE_MS,
  ASK_POLL_MS,
  ASK_TIMEOUT_MS,
  getIssuePanel,
  getReviewFeedback,
  openSolutionForm,
  rewriteSolution,
  saveSolutionDraft,
  submitSolution,
  SUBMIT_RETRY_DELAYS_MS,
} from './tsolveClient';

jest.mock('@forge/bridge', () => ({ invokeRemote: jest.fn() }));

const SECTIONS = { symptoms: 'Drops', rootCause: 'Old', resolutionSteps: 'Update', workaround: '', scopeOfApplicability: 'All' };
const TICKET = { id: 't-1', ticketId: 'ITSUP-12', status: 'PENDING_REVIEW' };

const answer = (status, body) => ({ status, headers: {}, body });

describe('tsolveClient — Solution Form and Issue Panel', () => {
  let sleep;

  beforeEach(() => {
    invokeRemote.mockReset();
    sleep = jest.fn().mockResolvedValue(undefined);
  });

  afterEach(() => jest.useRealTimers());

  it('AC-002.1 sends the issue text to open the form', async () => {
    invokeRemote.mockResolvedValue(answer(200, { mayOpen: true, origin: 'AI_DRAFT', sections: SECTIONS, message: null }));

    const form = await openSolutionForm('ITSUP-12', { summary: 'VPN', description: 'After sleep' });

    expect(form.origin).toBe('AI_DRAFT');
    expect(invokeRemote).toHaveBeenCalledWith(
      expect.objectContaining({
        path: '/api/v1/jira-app/issues/ITSUP-12/solution-form',
        method: 'POST',
        body: { summary: 'VPN', description: 'After sleep' },
      }),
    );
  });

  it('AC-012.2 asks to open the resubmission form', async () => {
    invokeRemote.mockResolvedValue(answer(200, { mayOpen: true, origin: 'TICKET', sections: SECTIONS, message: null }));

    await openSolutionForm('ITSUP-12', { summary: 'VPN', description: 'After sleep', resubmit: true });

    expect(invokeRemote).toHaveBeenCalledWith(
      expect.objectContaining({ body: { summary: 'VPN', description: 'After sleep', resubmit: true } }),
    );
  });

  it('AC-012.4 sends resubmit with Finish', async () => {
    invokeRemote.mockResolvedValue(answer(201, TICKET));

    await submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS, resubmit: true }, { sleep });

    expect(invokeRemote).toHaveBeenCalledWith(
      expect.objectContaining({ body: { summary: 'VPN', sections: SECTIONS, resubmit: true } }),
    );
  });

  it('AC-012.4 counts a 409 MSG06 after a retry as resubmitted once the ticket is back in Pending review', async () => {
    invokeRemote
      .mockResolvedValueOnce(answer(502, {}))
      .mockResolvedValueOnce(answer(409, { code: 'MSG06' }))
      .mockResolvedValueOnce(answer(200, { linked: true, ticketStatus: 'PENDING_REVIEW' }));

    await expect(
      submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS, resubmit: true }, { sleep }),
    ).resolves.toMatchObject({ ticketId: 'ITSUP-12', status: 'PENDING_REVIEW' });
  });

  it('AC-012.5 gives 409 MSG06 back when the ticket is no longer in Changes requested', async () => {
    invokeRemote.mockResolvedValue(answer(409, { code: 'MSG06' }));

    await expect(
      submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS, resubmit: true }, { sleep }),
    ).rejects.toMatchObject({ code: 'MSG06', status: 409 });
    expect(invokeRemote).toHaveBeenCalledTimes(1);
  });

  it('AC-004.1 submits once when T-Solve answers', async () => {
    invokeRemote.mockResolvedValue(answer(201, TICKET));

    await expect(submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS }, { sleep })).resolves.toEqual(TICKET);
    expect(invokeRemote).toHaveBeenCalledTimes(1);
    expect(sleep).not.toHaveBeenCalled();
  });

  it('AC-004.4 retries 3 times then gives MSG69', async () => {
    invokeRemote.mockResolvedValue(answer(503, { code: 'MSG06' }));

    await expect(submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS }, { sleep })).rejects.toMatchObject({
      code: 'MSG69',
    });
    expect(invokeRemote).toHaveBeenCalledTimes(4);
    expect(sleep.mock.calls.map(([ms]) => ms)).toEqual(SUBMIT_RETRY_DELAYS_MS);
  });

  it('AC-004.4 succeeds when a retry gets through', async () => {
    invokeRemote.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(answer(201, TICKET));

    await expect(submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS }, { sleep })).resolves.toEqual(TICKET);
    expect(invokeRemote).toHaveBeenCalledTimes(2);
  });

  it('AC-004.3 counts a 409 after a retry as submitted, since an earlier try got through', async () => {
    invokeRemote.mockResolvedValueOnce(answer(502, {})).mockResolvedValueOnce(answer(409, { code: 'MSG31' }));

    await expect(submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS }, { sleep })).resolves.toMatchObject({
      ticketId: 'ITSUP-12',
    });
  });

  it('gives MSG40 back after a retry instead of counting it as submitted (BUG-233)', async () => {
    invokeRemote.mockResolvedValueOnce(answer(502, {})).mockResolvedValueOnce(answer(409, { code: 'MSG40' }));

    await expect(submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS }, { sleep })).rejects.toMatchObject({
      code: 'MSG40',
      status: 409,
    });
  });

  it('AC-004.3 gives a first 409 back without retrying', async () => {
    invokeRemote.mockResolvedValue(answer(409, { code: 'MSG31', params: { ticket_id: 'ITSUP-12' } }));

    await expect(submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS }, { sleep })).rejects.toMatchObject({
      code: 'MSG31',
      status: 409,
    });
    expect(invokeRemote).toHaveBeenCalledTimes(1);
  });

  it('AC-002.5 gives the server MSG01 with its field, without retrying', async () => {
    invokeRemote.mockResolvedValue(
      answer(400, { code: 'MSG01', params: { field_name: 'Root cause' }, fieldErrors: [{ field: 'rootCause' }] }),
    );

    await expect(submitSolution('ITSUP-12', { summary: 'VPN', sections: SECTIONS }, { sleep })).rejects.toMatchObject({
      code: 'MSG01',
      field: 'rootCause',
      params: { field_name: 'Root cause' },
    });
    expect(sleep).not.toHaveBeenCalled();
  });

  it('AC-005.1 saves the draft with PUT', async () => {
    invokeRemote.mockResolvedValue(answer(204, undefined));

    await saveSolutionDraft('ITSUP-12', SECTIONS);

    expect(invokeRemote).toHaveBeenCalledWith(
      expect.objectContaining({ path: '/api/v1/jira-app/issues/ITSUP-12/solution-draft', method: 'PUT', body: SECTIONS }),
    );
  });

  it('AC-007.4 keeps MSG68 of a failed rewrite', async () => {
    invokeRemote.mockResolvedValue(answer(503, { code: 'MSG68' }));

    await expect(rewriteSolution(SECTIONS)).rejects.toMatchObject({ code: 'MSG68' });
  });

  it('AC-007.1 posts the sections then polls every 1.5 seconds until DONE returns all five sections', async () => {
    jest.useFakeTimers();
    const started = Date.now();
    const polls = [];
    const rewritten = { ...SECTIONS, symptoms: 'The connection drops.' };
    invokeRemote.mockImplementation(({ method }) => {
      if (method === 'POST') return Promise.resolve(answer(202, { jobId: 'rewrite/1 ?' }));
      polls.push(Date.now() - started);
      return Promise.resolve(answer(200, polls.length < 3 ? { status: 'PENDING' } : { status: 'DONE', sections: rewritten }));
    });

    const result = rewriteSolution(SECTIONS);
    await jest.advanceTimersByTimeAsync(ASK_POLL_MS - 1);
    expect(invokeRemote).toHaveBeenCalledTimes(1);
    await jest.advanceTimersByTimeAsync(1 + 2 * ASK_POLL_MS);

    await expect(result).resolves.toEqual(rewritten);
    expect(polls).toEqual([1500, 3000, 4500]);
    expect(invokeRemote.mock.calls[0][0]).toEqual({
      path: '/api/v1/jira-app/solution-rewrites', method: 'POST',
      headers: { 'Content-Type': 'application/json' }, body: SECTIONS,
    });
    expect(invokeRemote.mock.calls[1][0]).toEqual({
      path: '/api/v1/jira-app/solution-rewrites/rewrite%2F1%20%3F', method: 'GET',
    });
    expect(jest.getTimerCount()).toBe(0);
  });

  it.each([
    ['FAILED', 200, { status: 'FAILED' }],
    ['GET 404', 404, { code: 'MSG68' }],
    ['GET 503', 503, { code: 'MSG06' }],
    ['GET 403', 403, { code: 'MSG08' }],
    ['unknown status', 200, { status: 'UNKNOWN' }],
    ['missing sections', 200, { status: 'DONE' }],
    ['incomplete sections', 200, { status: 'DONE', sections: { symptoms: 'Only one' } }],
    ['non-string section', 200, { status: 'DONE', sections: { ...SECTIONS, workaround: null } }],
  ])('AC-007.4 maps %s to MSG68 and stops polling', async (_, status, body) => {
    invokeRemote.mockResolvedValueOnce(answer(202, { jobId: 'job-1' })).mockResolvedValue(answer(status, body));

    await expect(rewriteSolution(SECTIONS, { sleep })).rejects.toMatchObject({ name: 'TsolveError', code: 'MSG68' });
    expect(invokeRemote).toHaveBeenCalledTimes(2);
  });

  it.each([[400, 'MSG01'], [401, 'MSG08'], [403, 'MSG62'], [503, 'MSG06']])(
    'AC-007.4 maps POST %s %s to MSG68 without polling', async (status, code) => {
      invokeRemote.mockResolvedValue(answer(status, { code }));
      await expect(rewriteSolution(SECTIONS, { sleep })).rejects.toMatchObject({ code: 'MSG68', status });
      expect(invokeRemote).toHaveBeenCalledTimes(1);
    },
  );

  it.each([{}, { jobId: '' }, { jobId: 42 }])('AC-007.4 maps malformed POST %j to MSG68', async (body) => {
    invokeRemote.mockResolvedValue(answer(202, body));
    await expect(rewriteSolution(SECTIONS, { sleep })).rejects.toMatchObject({ code: 'MSG68' });
    expect(invokeRemote).toHaveBeenCalledTimes(1);
  });

  it.each(['POST', 'GET'])('AC-007.4 maps a rejected %s invocation to MSG68', async (method) => {
    if (method === 'GET') invokeRemote.mockResolvedValueOnce(answer(202, { jobId: 'job-1' }));
    invokeRemote.mockRejectedValue(new Error('Remote unavailable'));
    await expect(rewriteSolution(SECTIONS, { sleep })).rejects.toMatchObject({ code: 'MSG68' });
    expect(invokeRemote).toHaveBeenCalledTimes(method === 'POST' ? 1 : 2);
  });

  it.each(['POST', 'GET'])('AC-007.4 a %s that never answers gives MSG68 after 20 seconds', async (method) => {
    jest.useFakeTimers();
    if (method === 'GET') invokeRemote.mockResolvedValueOnce(answer(202, { jobId: 'job-1' }));
    invokeRemote.mockReturnValue(new Promise(() => {}));
    const result = expect(rewriteSolution(SECTIONS)).rejects.toMatchObject({ code: 'MSG68' });
    const calls = method === 'POST' ? 1 : 2;

    await jest.advanceTimersByTimeAsync((method === 'GET' ? ASK_POLL_MS : 0) + ASK_TIMEOUT_MS - 1);
    expect(invokeRemote).toHaveBeenCalledTimes(calls);
    expect(jest.getTimerCount()).toBe(1);
    await jest.advanceTimersByTimeAsync(1);
    await result;
    await jest.advanceTimersByTimeAsync(100000);
    expect(invokeRemote).toHaveBeenCalledTimes(calls);
    expect(jest.getTimerCount()).toBe(0);
  });

  it('AC-007.4 PENDING at 75 seconds gives MSG68 with no GET at or after the deadline', async () => {
    jest.useFakeTimers();
    const started = Date.now();
    const polls = [];
    invokeRemote.mockResolvedValueOnce(answer(202, { jobId: 'job-1' })).mockImplementation(() => {
      polls.push(Date.now() - started);
      return Promise.resolve(answer(200, { status: 'PENDING' }));
    });
    const result = expect(rewriteSolution(SECTIONS)).rejects.toMatchObject({ code: 'MSG68' });
    await jest.advanceTimersByTimeAsync(ASK_DEADLINE_MS);
    await result;
    expect(polls).toHaveLength(49);
    expect(polls.every((time) => time < 75000)).toBe(true);
    await jest.advanceTimersByTimeAsync(100000);
    expect(invokeRemote).toHaveBeenCalledTimes(50);
    expect(jest.getTimerCount()).toBe(0);
  });

  it('AC-007.4 counts POST time and cuts an in-flight GET at the overall deadline', async () => {
    jest.useFakeTimers();
    const started = Date.now();
    invokeRemote.mockImplementation(({ method }) => {
      if (method === 'POST') {
        return new Promise((resolve) => setTimeout(() => resolve(answer(202, { jobId: 'job-1' })), 19000));
      }
      return Date.now() - started < 70000
        ? Promise.resolve(answer(200, { status: 'PENDING' })) : new Promise(() => {});
    });
    const result = expect(rewriteSolution(SECTIONS)).rejects.toMatchObject({ code: 'MSG68' });
    await jest.advanceTimersByTimeAsync(ASK_DEADLINE_MS);
    await result;
    const calls = invokeRemote.mock.calls.length;
    await jest.advanceTimersByTimeAsync(30000);
    expect(invokeRemote).toHaveBeenCalledTimes(calls);
    expect(jest.getTimerCount()).toBe(0);
  });

  it('AC-007.4 supports injected sleep and now for the 75-second deadline', async () => {
    let time = 0;
    sleep.mockImplementation(async (ms) => { time += ms; });
    invokeRemote.mockResolvedValueOnce(answer(202, { jobId: 'job-1' }))
      .mockResolvedValue(answer(200, { status: 'PENDING' }));

    await expect(rewriteSolution(SECTIONS, { sleep, now: () => time })).rejects.toMatchObject({ code: 'MSG68' });
    expect(time).toBe(75000);
    expect(sleep).toHaveBeenCalledWith(1500, undefined);
    expect(invokeRemote).toHaveBeenCalledTimes(50);
  });

  it.each(['before POST', 'during POST', 'between polls', 'during GET'])(
    'AC-007.1 abort stops polling quietly %s', async (stage) => {
      jest.useFakeTimers();
      const controller = new AbortController();
      invokeRemote.mockResolvedValueOnce(answer(202, { jobId: 'job-1' })).mockResolvedValue(answer(200, { status: 'PENDING' }));
      if (stage === 'before POST') controller.abort();
      if (stage === 'during POST') invokeRemote.mockReset().mockReturnValue(new Promise(() => {}));
      if (stage === 'during GET') invokeRemote.mockReturnValue(new Promise(() => {}));
      const result = expect(rewriteSolution(SECTIONS, { signal: controller.signal })).rejects.toMatchObject({ name: 'AbortError' });
      await jest.advanceTimersByTimeAsync(stage === 'during GET' ? ASK_POLL_MS : 0);
      const calls = invokeRemote.mock.calls.length;
      expect(calls).toBe(stage === 'before POST' ? 0 : stage === 'during GET' ? 2 : 1);
      controller.abort();
      await result;
      await jest.advanceTimersByTimeAsync(100000);
      expect(invokeRemote).toHaveBeenCalledTimes(calls);
      expect(jest.getTimerCount()).toBe(0);
    },
  );

  it('AC-007.7 never logs Solution text on success or failure', async () => {
    const spies = ['log', 'info', 'warn', 'error', 'debug'].map((level) => jest.spyOn(console, level).mockImplementation(() => {}));
    try {
      invokeRemote.mockResolvedValueOnce(answer(202, { jobId: 'job-1' })).mockResolvedValue(answer(200, { status: 'DONE', sections: SECTIONS }));
      await rewriteSolution(SECTIONS, { sleep });
      invokeRemote.mockRejectedValue(new Error('Private Solution text'));
      await rewriteSolution(SECTIONS, { sleep }).catch(() => {});
      spies.forEach((spy) => expect(spy).not.toHaveBeenCalled());
    } finally {
      spies.forEach((spy) => spy.mockRestore());
    }
  });

  it('AC-008.1 reads the panel state', async () => {
    invokeRemote.mockResolvedValue(answer(200, { linked: false }));

    await expect(getIssuePanel('ITSUP-12')).resolves.toEqual({ linked: false });
  });

  it('AC-008.7 reads the change request, or null', async () => {
    const msg64 = { code: 'MSG64', params: { manager_name: 'Le Minh', comment: 'Add steps' } };
    invokeRemote.mockResolvedValueOnce(answer(200, { feedback: msg64 })).mockResolvedValueOnce(answer(200, { feedback: null }));

    await expect(getReviewFeedback('ITSUP-12')).resolves.toEqual(msg64);
    await expect(getReviewFeedback('ITSUP-12')).resolves.toBeNull();
  });
});
