import { invokeRemote } from '@forge/bridge';
import {
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

  it('gives MSG40 back after a retry instead of counting it as submitted (BUG-215)', async () => {
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
